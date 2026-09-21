-- UAT seed. Password for all users: CajamarcaUAT!2026
INSERT INTO app_user(id,username,password_hash,roles,display_name,instance_country_id,active) VALUES
('00000000-0000-0000-0000-000000000001','don','$2a$10$W8uMWFQLly1v1Wk7MNs8B.pLzHpjkRg5T2gI5hGtnKQjBeQx0o91.','DIRECTOR_OPERACIONES_NACIONAL','Director de Operaciones Nacional','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000002','dnacional','$2a$10$W8uMWFQLly1v1Wk7MNs8B.pLzHpjkRg5T2gI5hGtnKQjBeQx0o91.','DIRECTOR_NACIONAL','Director Nacional','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000003','dzonal','$2a$10$W8uMWFQLly1v1Wk7MNs8B.pLzHpjkRg5T2gI5hGtnKQjBeQx0o91.','DIRECTOR_ZONAL','Director Zonal de Operaciones','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000004','coord','$2a$10$W8uMWFQLly1v1Wk7MNs8B.pLzHpjkRg5T2gI5hGtnKQjBeQx0o91.','COORDINADOR_COMPANIA','Coordinador de Compañía','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000005','asistente','$2a$10$W8uMWFQLly1v1Wk7MNs8B.pLzHpjkRg5T2gI5hGtnKQjBeQx0o91.','ASISTENTE_COORDINACION','Asistente de Coordinación','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000006','supervisor','$2a$10$W8uMWFQLly1v1Wk7MNs8B.pLzHpjkRg5T2gI5hGtnKQjBeQx0o91.','SUPERVISOR_SEGURIDAD','Supervisor de Seguridad','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000007','agente','$2a$10$W8uMWFQLly1v1Wk7MNs8B.pLzHpjkRg5T2gI5hGtnKQjBeQx0o91.','AGENTE_SEGURIDAD','Agente de Seguridad Tier III','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000008','cliente','$2a$10$W8uMWFQLly1v1Wk7MNs8B.pLzHpjkRg5T2gI5hGtnKQjBeQx0o91.','CLIENTE','Usuario Cliente UAT','11111111-1111-1111-1111-111111111111',true);

INSERT INTO company(id,instance_country_id,code,name,status,required_change_count,created_at,updated_at) VALUES
('20000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','COM-001','Galvarino','ACTIVE',0,now(),now());

INSERT INTO service(id,instance_country_id,code,name,client_name,commercial_status,config_status,created_at,updated_at) VALUES
('30000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','SER-001','Seguridad Física Estática','Telconet','ACTIVE','CONFIGURED',now(),now());

INSERT INTO point(id,instance_country_id,service_id,code,name,province,city,client_name,company_id,status,created_at,updated_at) VALUES
('40000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','30000000-0000-0000-0000-000000000001','PTO-001','Telco-City','Guayas','Guayaquil','Telconet','20000000-0000-0000-0000-000000000001','ACTIVE',now(),now()),
('40000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','30000000-0000-0000-0000-000000000001','PTO-002','Telecom Tower','Guayas','Guayaquil','Telconet','20000000-0000-0000-0000-000000000001','ACTIVE',now(),now());

INSERT INTO post(id,instance_country_id,point_id,code,name,format,fhe,tier,config_status,created_at,updated_at) VALUES
('50000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','40000000-0000-0000-0000-000000000001','GGTT01','Control de Acceso Principal','24/7',3.00,'IV','CONFIGURED',now(),now()),
('50000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','40000000-0000-0000-0000-000000000001','GGTT02','Perímetro Norte','24/7',3.00,'III','CONFIGURED',now(),now()),
('50000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','40000000-0000-0000-0000-000000000002','GGTT-2-01','Control de Acceso','12/7',2.00,'II','CONFIGURED',now(),now());

INSERT INTO consignment(id,instance_country_id,code,point_id,post_id,title,instruction,priority,status,validity_type,application_type,published_at,created_at,updated_at) VALUES
('60000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','CON-000001','40000000-0000-0000-0000-000000000001',NULL,'Verificación de credenciales','Verificar credencial vigente de toda persona que ingrese por Control de Acceso Principal.','HIGH','ACTIVE','PERMANENT','ALL_TIME',now(),now(),now());

INSERT INTO regesep_version(id,instance_country_id,point_id,version,code,status,effective_at,trigger_type,source_manifest_json,created_at,updated_at) VALUES
('70000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','40000000-0000-0000-0000-000000000001',1,'REG-GGTT-v001','CURRENT',now(),'UAT_BASELINE','{"ats":{"status":"PENDING"},"consignments":["CON-000001"],"posts":["GGTT01","GGTT02"]}',now(),now());
