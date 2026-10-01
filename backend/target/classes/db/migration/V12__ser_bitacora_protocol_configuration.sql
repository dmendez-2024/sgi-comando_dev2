-- SGI: Comando / SER v0.6 — Configuración de Bitácora por Puesto.
-- REGESEP configura la regla; la ejecución real de Bitácora pertenece a Operación.

CREATE TABLE IF NOT EXISTS logbook_protocol (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL REFERENCES post(id),
  code varchar(32) NOT NULL,
  name varchar(180) NOT NULL,
  object_type varchar(16) NOT NULL CHECK (object_type IN ('PAX','VHL','CONT')),
  application_type varchar(16) NOT NULL CHECK (application_type IN ('INGRESO','EGRESO','AMBOS')),
  description varchar(1000) NOT NULL DEFAULT '',
  status varchar(24) NOT NULL CHECK (status IN ('BORRADOR','VIGENTE','SUSPENDIDO')),
  version_no integer NOT NULL DEFAULT 1 CHECK (version_no >= 1),
  identification_logic varchar(8) NOT NULL DEFAULT 'ALL' CHECK (identification_logic IN ('ALL','ANY')),
  verification_logic varchar(8) NOT NULL DEFAULT 'ALL' CHECK (verification_logic IN ('ALL','ANY')),
  auth_preapproval boolean NOT NULL DEFAULT true,
  auth_client boolean NOT NULL DEFAULT true,
  auth_supervisor boolean NOT NULL DEFAULT false,
  white_list_enabled boolean NOT NULL DEFAULT true,
  black_list_enabled boolean NOT NULL DEFAULT true,
  capture_manual boolean NOT NULL DEFAULT true,
  capture_qr boolean NOT NULL DEFAULT true,
  capture_barcode boolean NOT NULL DEFAULT false,
  capture_nfc boolean NOT NULL DEFAULT false,
  capture_automatic boolean NOT NULL DEFAULT false,
  source_model_type varchar(32) NOT NULL DEFAULT 'LOCAL',
  source_model_name varchar(180),
  last_published_at timestamptz,
  updated_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_logbook_protocol_code_tenant UNIQUE(instance_country_id,code)
);
CREATE INDEX IF NOT EXISTS ix_logbook_protocol_post ON logbook_protocol(instance_country_id,post_id);

CREATE TABLE IF NOT EXISTS logbook_protocol_field (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  protocol_id uuid NOT NULL REFERENCES logbook_protocol(id) ON DELETE CASCADE,
  section varchar(24) NOT NULL CHECK (section IN ('IDENTIFICACION','VERIFICACION')),
  sort_order integer NOT NULL CHECK (sort_order >= 1),
  name varchar(160) NOT NULL,
  description varchar(1000) NOT NULL DEFAULT '',
  field_type varchar(24) NOT NULL CHECK (field_type IN ('DOCUMENTO','IMAGEN','TEXTO','SELECCION','CODIGO','OTRO')),
  required boolean NOT NULL DEFAULT false,
  evidence_required boolean NOT NULL DEFAULT false,
  capture_mode varchar(32) NOT NULL CHECK (capture_mode IN ('MANUAL','CAMARA','MANUAL_QR','QR','CODIGO_BARRAS','NFC')),
  custom_field boolean NOT NULL DEFAULT false,
  standard_image_original_name varchar(255),
  standard_image_content_type varchar(100),
  standard_image_data bytea,
  standard_image_version integer NOT NULL DEFAULT 0 CHECK (standard_image_version >= 0),
  standard_image_notes varchar(1000),
  visint_enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_logbook_protocol_field_protocol ON logbook_protocol_field(instance_country_id,protocol_id,section,sort_order);

-- Semilla UAT: cuatro protocolos para GGTT01 y uno para GGTT02.
INSERT INTO logbook_protocol(
  id,instance_country_id,post_id,code,name,object_type,application_type,description,status,version_no,
  identification_logic,verification_logic,auth_preapproval,auth_client,auth_supervisor,white_list_enabled,black_list_enabled,
  capture_manual,capture_qr,capture_barcode,capture_nfc,capture_automatic,source_model_type,source_model_name,last_published_at,
  updated_by_username,created_at,updated_at
) VALUES
('81000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000001','PRO-BA-0001','Ingreso de visitantes','PAX','INGRESO','Procedimiento para el control de ingreso de visitantes al punto, con validación de identidad y autorización correspondiente.','VIGENTE',2,'ALL','ALL',true,true,false,true,true,true,true,false,false,false,'LOCAL',NULL,now(),'coord',now(),now()),
('81000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000001','PRO-BA-0002','Salida de visitantes','PAX','EGRESO','Procedimiento para validar la salida de visitantes y cierre del registro de ingreso.','VIGENTE',1,'ALL','ALL',false,true,false,true,true,true,true,false,false,false,'LOCAL',NULL,now(),'coord',now(),now()),
('81000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000001','PRO-BA-0003','Ingreso vehicular','VHL','INGRESO','Control de ingreso de vehículos autorizados al punto.','VIGENTE',3,'ALL','ALL',true,true,false,true,true,true,true,true,false,false,'COMPANIA','Control vehicular estándar',now(),'coord',now(),now()),
('81000000-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000001','PRO-BA-0004','Control de contenedores','CONT','AMBOS','Control de ingreso y egreso de contenedores.','BORRADOR',1,'ALL','ALL',true,true,true,true,true,true,false,true,false,false,'CAJAMARCA','Control básico de contenedores',NULL,'coord',now(),now()),
('81000000-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000002','PRO-BA-0005','Control de perímetro','PAX','AMBOS','Registro de personas detectadas o autorizadas en el perímetro asignado.','VIGENTE',1,'ALL','ANY',false,true,true,false,true,true,false,false,false,false,'LOCAL',NULL,now(),'coord',now(),now())
ON CONFLICT(instance_country_id,code) DO NOTHING;

-- Campos base del protocolo PRO-BA-0001.
INSERT INTO logbook_protocol_field(
  id,instance_country_id,protocol_id,section,sort_order,name,description,field_type,required,evidence_required,capture_mode,custom_field,
  standard_image_original_name,standard_image_content_type,standard_image_data,standard_image_version,standard_image_notes,visint_enabled,created_at,updated_at
) VALUES
('82000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','IDENTIFICACION',1,'Cédula','Documento oficial de identidad del visitante. Debe estar vigente y en buen estado.','DOCUMENTO',true,true,'MANUAL_QR',false,NULL,NULL,NULL,0,'Documento completo, sin reflejos, cuatro esquinas visibles, texto legible y fotografía nítida.',false,now(),now()),
('82000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','IDENTIFICACION',2,'Pasaporte','Documento de viaje cuando aplique.','DOCUMENTO',false,true,'MANUAL',false,NULL,NULL,NULL,0,'Documento completo y legible.',false,now(),now()),
('82000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','IDENTIFICACION',3,'Credencial','Credencial emitida por el cliente o tercero autorizado.','DOCUMENTO',true,true,'MANUAL_QR',false,NULL,NULL,NULL,0,'Credencial completa y número visible.',false,now(),now()),
('82000000-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','IDENTIFICACION',4,'Rostro','Fotografía frontal de la persona.','IMAGEN',true,true,'CAMARA',false,NULL,NULL,NULL,0,'Rostro centrado, iluminación suficiente y sin obstrucciones.',false,now(),now()),
('82000000-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','VERIFICACION',1,'Persona en lista autorizada','Validar que la persona conste en la lista aplicable.','TEXTO',true,false,'MANUAL',false,NULL,NULL,NULL,0,'',false,now(),now()),
('82000000-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','VERIFICACION',2,'Empresa','Empresa u organización a la que pertenece.','TEXTO',true,false,'MANUAL',false,NULL,NULL,NULL,0,'',false,now(),now()),
('82000000-0000-0000-0000-000000000007','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','VERIFICACION',3,'Motivo de visita','Motivo declarado y validado del ingreso.','TEXTO',true,false,'MANUAL',false,NULL,NULL,NULL,0,'',false,now(),now()),
('82000000-0000-0000-0000-000000000008','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','VERIFICACION',4,'Persona anfitriona','Persona responsable de recibir al visitante.','TEXTO',true,false,'MANUAL',false,NULL,NULL,NULL,0,'',false,now(),now()),
('82000000-0000-0000-0000-000000000009','11111111-1111-1111-1111-111111111111','81000000-0000-0000-0000-000000000001','VERIFICACION',5,'Vigencia de autorización','Verificar vigencia de la autorización aplicable.','TEXTO',true,false,'MANUAL',false,NULL,NULL,NULL,0,'',false,now(),now())
ON CONFLICT(id) DO NOTHING;
