-- UAT/PRESENTACION: un único protocolo vigente para GGTT01 y tres patrullas de valor.
-- Idempotente y acotado al tenant/punto/puesto indicados.
BEGIN;

DO $fixture$
DECLARE
    v_tenant uuid := '11111111-1111-1111-1111-111111111111';
    v_protocol uuid := '66ab35c4-4ac0-441b-a5dc-c63740890659';
    v_post uuid;
    v_point uuid;
BEGIN
    SELECT po.id, po.point_id INTO v_post, v_point
      FROM post po
      JOIN point pt ON pt.id=po.point_id AND pt.instance_country_id=po.instance_country_id
     WHERE po.instance_country_id=v_tenant AND po.code='GGTT01' AND pt.code='PTO-001';
    IF v_post IS NULL THEN RAISE EXCEPTION 'No existe el puesto UAT PTO-001/GGTT01'; END IF;
    IF NOT EXISTS (SELECT 1 FROM patrol_protocol WHERE id=v_protocol AND instance_country_id=v_tenant) THEN
        RAISE EXCEPTION 'No existe el protocolo base PRO-PAT-0004';
    END IF;

    UPDATE patrol_protocol p SET status='INACTIVO',updated_at=current_timestamp,updated_by_username='codex-uat'
     WHERE p.instance_country_id=v_tenant AND p.id<>v_protocol AND EXISTS (
       SELECT 1 FROM patrol_protocol_post_scope s WHERE s.instance_country_id=p.instance_country_id AND s.protocol_id=p.id AND s.post_id=v_post
     ) AND p.status='ACTIVO';

    UPDATE patrol_protocol SET name='Protocolo Integral de Prevención y Control',
      description='Prevención, control de accesos, protección de activos críticos y cierre seguro del puesto.',
      status='ACTIVO',last_published_at=current_timestamp,updated_at=current_timestamp,updated_by_username='codex-uat'
     WHERE id=v_protocol AND instance_country_id=v_tenant;

    INSERT INTO patrol_protocol_post_scope(id,instance_country_id,protocol_id,post_id,created_at,updated_at)
    VALUES('d2000000-0000-0000-0000-000000000001',v_tenant,v_protocol,v_post,current_timestamp,current_timestamp)
    ON CONFLICT DO NOTHING;

    UPDATE patrol_definition SET code='PAT-001',name='Apertura Segura y Control de Accesos',
      description='Verifica que el puesto inicie operaciones sin novedades, con accesos controlados y equipos disponibles.',
      structure_type='CLOSED',sequence_type='STRICT',schedule_type='PROGRAMMED',window_start='05:00',window_end='05:30',
      repetitions=1,status='ACTIVO',updated_at=current_timestamp,updated_by_username='codex-uat'
     WHERE id='c7cd0a4f-4d59-4309-ba12-6c6f18c22c51' AND instance_country_id=v_tenant;

    INSERT INTO patrol_definition(id,instance_country_id,point_id,protocol_id,code,name,description,structure_type,sequence_type,status,version,
      schedule_type,window_start,window_end,repetitions,version_no,updated_by_username,created_at,updated_at)
    VALUES
      ('a2000000-0000-0000-0000-000000000002',v_tenant,v_point,v_protocol,'PAT-002','Protección de Activos Críticos',
       'Confirma la integridad de áreas sensibles, energía de respaldo y bienes bajo custodia.','CLOSED','STRICT','ACTIVO',1,'PROGRAMMED','13:00','13:30',1,1,'codex-uat',current_timestamp,current_timestamp),
      ('a2000000-0000-0000-0000-000000000003',v_tenant,v_point,v_protocol,'PAT-003','Cierre Perimetral y Prevención Nocturna',
       'Reduce riesgos al cierre mediante verificación perimetral, iluminación y ausencia de condiciones inseguras.','CLOSED','STRICT','ACTIVO',1,'PROGRAMMED','19:00','19:45',1,1,'codex-uat',current_timestamp,current_timestamp)
    ON CONFLICT (id) DO UPDATE SET name=excluded.name,description=excluded.description,schedule_type=excluded.schedule_type,
      window_start=excluded.window_start,window_end=excluded.window_end,status='ACTIVO',updated_at=current_timestamp,updated_by_username='codex-uat';

    UPDATE patrol_checkpoint SET sequence_no=1,code='H01',name='Validación del Acceso Principal',
      description='Confirmar puertas, barreras, cerraduras y área de ingreso sin señales de manipulación.',origin_mode='FIELD',control_type='INSPECCION_VISUAL',requires_evidence=true,updated_at=current_timestamp
     WHERE id='5eb67fc1-9e7c-4a20-9d45-2ecd055e1b3c' AND instance_country_id=v_tenant;
    UPDATE patrol_checkpoint SET sequence_no=2,code='H02',name='Puesto y Equipos Operativos',
      description='Verificar radio, iluminación, bitácora y elementos de respuesta disponibles.',origin_mode='FIELD',control_type='INSPECCION_VISUAL',requires_evidence=true,updated_at=current_timestamp
     WHERE id='ee0e93df-d2a0-4816-b0c2-472f2b8dc28f' AND instance_country_id=v_tenant;

    INSERT INTO patrol_checkpoint(id,instance_country_id,patrol_definition_id,sequence_no,code,name,description,origin_mode,control_type,
      requires_evidence,validation_rule_json,standard_image_version,standard_image_notes,visint_enabled,created_at,updated_at)
    VALUES
      ('b2000000-0000-0000-0000-000000000003',v_tenant,'c7cd0a4f-4d59-4309-ba12-6c6f18c22c51',3,'H03','Registro de Novedades de Apertura','Confirmar que no existen novedades pendientes o registrarlas antes de iniciar la operación.','FIELD','CONFIRMACION',true,'{}',0,'',false,current_timestamp,current_timestamp),
      ('b2000000-0000-0000-0000-000000000004',v_tenant,'a2000000-0000-0000-0000-000000000002',1,'H01','Integridad de Bodega y Bienes','Revisar sellos, cerraduras y condiciones visibles de los bienes bajo custodia.','FIELD','INSPECCION_VISUAL',true,'{}',0,'',false,current_timestamp,current_timestamp),
      ('b2000000-0000-0000-0000-000000000005',v_tenant,'a2000000-0000-0000-0000-000000000002',2,'H02','Energía y Respaldo Operativo','Verificar tablero visible, generador o respaldo energético sin alarmas ni fugas.','FIELD','INSPECCION_VISUAL',true,'{}',0,'',false,current_timestamp,current_timestamp),
      ('b2000000-0000-0000-0000-000000000006',v_tenant,'a2000000-0000-0000-0000-000000000002',3,'H03','Zona Restringida sin Anomalías','Confirmar ausencia de personas no autorizadas y condiciones inseguras.','FIELD','CONFIRMACION',true,'{}',0,'',false,current_timestamp,current_timestamp),
      ('b2000000-0000-0000-0000-000000000007',v_tenant,'a2000000-0000-0000-0000-000000000003',1,'H01','Cerramiento y Puntos Vulnerables','Inspeccionar malla, muros, portones y puntos de posible intrusión.','FIELD','INSPECCION_VISUAL',true,'{}',0,'',false,current_timestamp,current_timestamp),
      ('b2000000-0000-0000-0000-000000000008',v_tenant,'a2000000-0000-0000-0000-000000000003',2,'H02','Iluminación y Visibilidad Nocturna','Comprobar iluminación exterior y visibilidad de zonas críticas.','FIELD','INSPECCION_VISUAL',true,'{}',0,'',false,current_timestamp,current_timestamp),
      ('b2000000-0000-0000-0000-000000000009',v_tenant,'a2000000-0000-0000-0000-000000000003',3,'H03','Cierre Seguro sin Novedades','Confirmar accesos cerrados y documentar cualquier condición que requiera seguimiento.','FIELD','CONFIRMACION',true,'{}',0,'',false,current_timestamp,current_timestamp)
    ON CONFLICT (id) DO UPDATE SET name=excluded.name,description=excluded.description,control_type=excluded.control_type,
      requires_evidence=true,updated_at=current_timestamp;

    DELETE FROM patrol_checkpoint_rule WHERE instance_country_id=v_tenant AND checkpoint_id IN (
      '5eb67fc1-9e7c-4a20-9d45-2ecd055e1b3c','ee0e93df-d2a0-4816-b0c2-472f2b8dc28f',
      'b2000000-0000-0000-0000-000000000003','b2000000-0000-0000-0000-000000000004','b2000000-0000-0000-0000-000000000005',
      'b2000000-0000-0000-0000-000000000006','b2000000-0000-0000-0000-000000000007','b2000000-0000-0000-0000-000000000008','b2000000-0000-0000-0000-000000000009');

    INSERT INTO patrol_checkpoint_rule(id,instance_country_id,checkpoint_id,sort_order,rule_type,required,evidence_required,created_at,updated_at)
    SELECT ('c2'||lpad(row_number() over()::text,30,'0'))::uuid,v_tenant,cp.id,r.n,r.rule_type,true,(r.rule_type='FOTOGRAFIA'),current_timestamp,current_timestamp
      FROM patrol_checkpoint cp CROSS JOIN (VALUES(1,'INSPECCION_VISUAL'),(2,'FOTOGRAFIA'),(3,'CONFIRMACION')) r(n,rule_type)
     WHERE cp.instance_country_id=v_tenant AND cp.patrol_definition_id IN (
       'c7cd0a4f-4d59-4309-ba12-6c6f18c22c51','a2000000-0000-0000-0000-000000000002','a2000000-0000-0000-0000-000000000003');
END
$fixture$;

COMMIT;
