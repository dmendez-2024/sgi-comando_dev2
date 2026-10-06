create table if not exists operator_logbook_record (
    id uuid primary key,
    instance_country_id uuid not null,
    assignment_id uuid not null,
    point_id uuid not null,
    post_id uuid not null,
    employee_id uuid not null,
    username varchar(160) not null,
    protocol_id uuid not null,
    protocol_version integer not null,
    accreditation_id uuid not null,
    captured_at timestamptz not null,
    received_at timestamptz not null default current_timestamp,
    status varchar(40) not null default 'REGISTERED',
    comments varchar(500),
    field_values_json text not null default '{}',
    evidence_refs_json text not null default '[]',
    payload_hash varchar(64) not null,
    correlation_id uuid not null,
    constraint fk_operator_logbook_protocol foreign key (protocol_id) references logbook_protocol(id),
    constraint fk_operator_logbook_accreditation foreign key (accreditation_id) references logbook_accreditation(id)
);

create unique index if not exists ux_operator_logbook_record_tenant_id
    on operator_logbook_record(instance_country_id,id);
create index if not exists ix_operator_logbook_record_tenant_captured
    on operator_logbook_record(instance_country_id,captured_at desc);
create index if not exists ix_operator_logbook_record_assignment
    on operator_logbook_record(instance_country_id,assignment_id);
