-- Impulsos: reglas versionadas, evaluación/adjudicación y ledger por Operador, todo en SGI: Comando (sin CORE por ahora).
-- Valores iniciales tomados de la hoja AdS de "SGI Operador Tareas": Rango = [amount_min, amount_max], Promedio = reference_average.
-- Son editables en la base; una regla nueva o un cambio de valores se registra como otra versión (version_no + 1).

create table impulse_rule (
    id uuid primary key,
    instance_country_id uuid not null,
    code varchar(40) not null,
    version_no int not null,
    skill_code varchar(40) not null,
    action_code varchar(40) not null,
    description varchar(300) not null,
    source_type varchar(40) not null,
    requires_visint_pass boolean not null default true,
    probability numeric(5,4) not null default 1,
    amount_min numeric(8,2) not null default 0,
    amount_max numeric(8,2) not null default 0,
    reference_average numeric(8,2),
    params_json text not null default '{}',
    active boolean not null default true,
    effective_from timestamptz not null default now(),
    created_at timestamptz not null default now(),
    constraint uq_impulse_rule_version unique (instance_country_id, code, version_no),
    constraint ck_impulse_rule_probability check (probability >= 0 and probability <= 1),
    constraint ck_impulse_rule_amount check (amount_min >= 0 and amount_max >= amount_min)
);

-- Una evaluación por (regla, hecho operativo): el sorteo se hace una sola vez y los reintentos no duplican Impulsos.
-- decision: AWARDED (amount > 0), NO_AWARD (elegible sin premio) o NOT_ELIGIBLE (no cumple la regla o VISINT no aprobó).
create table impulse_evaluation (
    id uuid primary key,
    instance_country_id uuid not null,
    rule_id uuid not null references impulse_rule(id),
    rule_code varchar(40) not null,
    rule_version int not null,
    skill_code varchar(40) not null,
    source_type varchar(40) not null,
    source_id uuid not null,
    employee_id uuid not null,
    assignment_id uuid,
    occurred_at timestamptz not null,
    visint_status varchar(24),
    decision varchar(16) not null,
    reason varchar(200),
    draw numeric(6,5),
    amount numeric(8,2) not null default 0,
    evaluated_at timestamptz not null default now(),
    constraint uq_impulse_evaluation_source unique (instance_country_id, rule_code, source_type, source_id),
    constraint ck_impulse_evaluation_decision check (decision in ('AWARDED','NO_AWARD','NOT_ELIGIBLE')),
    constraint ck_impulse_evaluation_amount check (amount >= 0)
);
create index ix_impulse_evaluation_employee on impulse_evaluation (instance_country_id, employee_id, evaluated_at desc);

-- Reglas iniciales para cada Instancia PE. Las que no tienen un hecho operativo que las produzca quedan inactivas.
insert into impulse_rule (id, instance_country_id, code, version_no, skill_code, action_code, description, source_type,
                          requires_visint_pass, probability, amount_min, amount_max, reference_average, params_json, active)
select gen_random_uuid(), i.instance_country_id, r.code, 1, r.skill, r.action, r.description, r.source, r.visint, 1, r.amin, r.amax, r.avg, r.params, r.active
from instance_country_context i
cross join (values
    ('REL_A_TIEMPO',    'ASISTENCIA',       'RELIEF_ON_TIME',      'Relevo completo a tiempo',              'RELIEF',        false, 4.00, 6.00, 5.00, '{"toleranceMinutes":15}', true),
    ('REL_FOTO_CUERPO', 'PORTE',            'RELIEF_FULL_BODY',    'Foto de cuerpo completo en el relevo',  'RELIEF',        false, 4.00, 6.00, 5.00, '{}',                      true),
    ('BIT_CA_CORRECTO', 'CONTROL_ACCESO',   'LOGBOOK_RECORD',      'Cada control de acceso correcto',       'LOGBOOK',       true,  0.00, 1.00, 0.10, '{}',                      true),
    ('PAT_CORRECTA',    'PATRULLAS',        'PATROL_COMPLETED',    'Cada patrulla correcta',                'PATROL',        true,  0.00, 1.00, 0.50, '{}',                      true),
    ('CON_CORRECTA',    'CRITERIO',         'CONSIGNMENT_DONE',    'Cada consigna correcta',                'CONSIGNMENT',   true,  0.00, 1.00, 0.50, '{}',                      true),
    ('NOV_APROBADA',    'CRITERIO',         'NOVELTY_APPROVED',    'Cada novedad aprobada',                 'NOVELTY',       false, 1.00, 2.00, 1.70, '{}',                      false),
    ('PAT_KM',          'TACTICA',          'PATROL_KM',           'Cada km caminado en patrulla',          'PATROL_KM',     false, 0.00, 2.00, 1.00, '{}',                      false),
    ('LID_RESPONSABLE', 'LIDERAZGO',        'POINT_LEADER',        'Responsable de punto',                  'POINT_LEADER',  false, 0.00, 0.00, null, '{"formula":"5 x N ADS"}', false),
    ('QR_5_ESTRELLAS',  'ATENCION_CLIENTE', 'CUSTOMER_QR_5_STARS', 'Cada calificación de 5 estrellas por QR','CUSTOMER_QR',  false, 0.00, 1.00, 0.50, '{}',                      false)
) as r(code, skill, action, description, source, visint, amin, amax, avg, params, active);
