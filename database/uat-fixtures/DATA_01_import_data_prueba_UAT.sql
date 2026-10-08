-- DATA-PRUEBA.xls: carga directa PostgreSQL.
-- Regla aplicada: un point.code queda asociado al primer service.code encontrado en el Excel.
-- Ejecutar con psql, sin ejecutar contra producción sin respaldo:
-- psql -v instance_country_id='UUID-DE-LA-INSTANCIA' -f import_data_prueba.sql

\if :{?instance_country_id}
\else
\echo 'Debe indicar -v instance_country_id=UUID-DE-LA-INSTANCIA'
\quit
\endif

BEGIN;
SELECT set_config('app.import.instance_country_id', :'instance_country_id', true);

CREATE TEMP TABLE import_data_prueba (
  client_code text, client_name text, client_status text,
  service_code text, service_name text, service_status text,
  point_code text, point_name text, province text, city text, point_status text,
  post_code2 text, post_name text, post_format text, fhe numeric(8,2), tier text,
  post_status text, rotation_code text, cycle_length_days integer,
  post_code text, base_code text, source_signature text, discriminator text, post_sequence integer
) ON COMMIT DROP;

INSERT INTO import_data_prueba VALUES
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-4806', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-724', 'Esmeraldas', 'Esmeraldas', 'Esmeraldas', 'ACTIVE', 'PUE-CM-EC-26943', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'EETE1', 'EETE', 'ESMERALDAS|ESMERALDAS|TELCONETSA|ESMERALDAS', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-4806', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-724', 'Esmeraldas', 'Esmeraldas', 'Esmeraldas', 'ACTIVE', 'PUE-CM-EC-10252', 'Guardiania', '24/7', 3.0, 'I', 'ACTIVE', '4-2', 6, 'EETE2', 'EETE', 'ESMERALDAS|ESMERALDAS|TELCONETSA|ESMERALDAS', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2217', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1167', 'Telconet Babahoyo', 'Los Ríos', 'Babahoyo', 'ACTIVE', 'PUE-CM-EC-16813', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'LBTT1', 'LBTT', 'LOSRIOS|BABAHOYO|TELCONETSA|TELCONETBABAHOYO', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2217', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1167', 'Telconet Babahoyo', 'Los Ríos', 'Babahoyo', 'ACTIVE', 'PUE-CM-EC-20157', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'LBTT2', 'LBTT', 'LOSRIOS|BABAHOYO|TELCONETSA|TELCONETBABAHOYO', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3155', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1603', 'Ambato', 'Tungurahua', 'Ambato', 'ACTIVE', 'PUE-CM-EC-21243', 'Guardiania', '12/7', 1.33, 'I', 'ACTIVE', '6-2', 8, 'TATA1', 'TATA', 'TUNGURAHUA|AMBATO|TELCONETSA|AMBATO', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3155', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1603', 'Ambato', 'Tungurahua', 'Ambato', 'ACTIVE', 'PUE-CM-EC-21242', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'TATA2', 'TATA', 'TUNGURAHUA|AMBATO|TELCONETSA|AMBATO', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2282', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-125', 'Sucursal Salinas', 'Santa Elena', 'Salinas', 'ACTIVE', 'PUE-CM-EC-17192', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'SSTS1', 'SSTS', 'SANTAELENA|SALINAS|TELCONETSA|SUCURSALSALINAS', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2282', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-125', 'Sucursal Salinas', 'Santa Elena', 'Salinas', 'ACTIVE', 'PUE-CM-EC-1265', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'SSTS2', 'SSTS', 'SANTAELENA|SALINAS|TELCONETSA|SUCURSALSALINAS', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-4048', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1937', 'Gosseal', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-24720', 'Guardiania', '12/7', 1.33, 'I', 'ACTIVE', '6-2', 8, 'PQTG1', 'PQTG', 'PICHINCHA|QUITO|TELCONETSA|GOSSEAL', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3416', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1462', 'Telconet Guamaní', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-22163', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTT1', 'PQTT', 'PICHINCHA|QUITO|TELCONETSA|TELCONETGUAMANI', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3416', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1462', 'Telconet Guamaní', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-19764', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTT2', 'PQTT', 'PICHINCHA|QUITO|TELCONETSA|TELCONETGUAMANI', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3416', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1462', 'Telconet Guamaní', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-19671', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTT3', 'PQTT', 'PICHINCHA|QUITO|TELCONETSA|TELCONETGUAMANI', '', 3),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-207', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-112', 'Bodega UIO', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-1256', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTB1', 'PQTB', 'PICHINCHA|QUITO|TELCONETSA|BODEGAUIO', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-207', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-112', 'Bodega UIO', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-1257', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTB2', 'PQTB', 'PICHINCHA|QUITO|TELCONETSA|BODEGAUIO', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-4236', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1282', 'Anconcito', 'Santa Elena', 'Santa Elena', 'ACTIVE', 'PUE-CM-EC-25128', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'SSTA1', 'SSTA', 'SANTAELENA|SANTAELENA|TELCONETSA|ANCONCITO', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-4236', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1282', 'Anconcito', 'Santa Elena', 'Santa Elena', 'ACTIVE', 'PUE-CM-EC-17908', 'Guardiania', '12/7', 1.5, 'I', 'ACTIVE', '4-2', 6, 'SSTA2', 'SSTA', 'SANTAELENA|SANTAELENA|TELCONETSA|ANCONCITO', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-209', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-113', 'Aceitunos', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-1259', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTA1', 'PQTA', 'PICHINCHA|QUITO|TELCONETSA|ACEITUNOS', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-209', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-113', 'Aceitunos', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-20565', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTA2', 'PQTA', 'PICHINCHA|QUITO|TELCONETSA|ACEITUNOS', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-213', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-122', 'Sucursal  Cuenca', 'Azuay', 'Cuenca', 'ACTIVE', 'PUE-CM-EC-1264', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'ACTS1', 'ACTS', 'AZUAY|CUENCA|TELCONETSA|SUCURSALCUENCA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-215', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-111', 'Mariana de Jesus', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-1266', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTM1', 'PQTM', 'PICHINCHA|QUITO|TELCONETSA|MARIANADEJESUS', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-215', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-111', 'Mariana de Jesus', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-1267', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTM2', 'PQTM', 'PICHINCHA|QUITO|TELCONETSA|MARIANADEJESUS', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-215', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-111', 'Mariana de Jesus', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-1269', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTM3', 'PQTM', 'PICHINCHA|QUITO|TELCONETSA|MARIANADEJESUS', '', 3),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-579', 'Servicio de Guardianía Móvil 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-419', 'Machala', 'El Oro', 'Machala', 'ACTIVE', 'PUE-CM-EC-6584', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'EMTM1', 'EMTM', 'ELORO|MACHALA|TELCONETSA|MACHALA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3787', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1466', 'Machala', 'El Oro', 'Machala', 'ACTIVE', 'PUE-CM-EC-23802', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'EMTM2', 'EMTM', 'ELORO|MACHALA|TELCONETSA|MACHALA', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3770', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1868', 'Casa de Gerencia', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-23776', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTC1', 'PQTC', 'PICHINCHA|QUITO|TELCONETSA|CASADEGERENCIA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-4044', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1936', 'Tabiazo', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-24714', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTTA1', 'PQTT', 'PICHINCHA|QUITO|TELCONETSA|TABIAZO', 'A', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3285', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-819', 'Cuenca', 'Azuay', 'Cuenca', 'ACTIVE', 'PUE-CM-EC-21689', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'ACTC1', 'ACTC', 'AZUAY|CUENCA|TELCONETSA|CUENCA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3285', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-819', 'Cuenca', 'Azuay', 'Cuenca', 'ACTIVE', 'PUE-CM-EC-13512', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'ACTC2', 'ACTC', 'AZUAY|CUENCA|TELCONETSA|CUENCA', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2946', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-454', 'Bodega Quevedo', 'Los Ríos', 'Quevedo', 'ACTIVE', 'PUE-CM-EC-20369', 'Guardiania', '24/7', 2.8, 'I', 'ACTIVE', '5-2', 7, 'LQTB1', 'LQTB', 'LOSRIOS|QUEVEDO|TELCONETSA|BODEGAQUEVEDO', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2946', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-454', 'Bodega Quevedo', 'Los Ríos', 'Quevedo', 'ACTIVE', 'PUE-CM-EC-7212', 'Guardiania', '24/7', 2.8, 'I', 'ACTIVE', '5-2', 7, 'LQTB2', 'LQTB', 'LOSRIOS|QUEVEDO|TELCONETSA|BODEGAQUEVEDO', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-1462', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-719', 'Santo Domingo', 'Santo Domingo de los Tsáchilas', 'Santo Domingo', 'ACTIVE', 'PUE-CM-EC-10234', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'SSTSA1', 'SSTS', 'SANTODOMINGODELOSTSACHILAS|SANTODOMINGO|TELCONETSA|SANTODOMINGO', 'A', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-1836', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-923', 'Riobamba', 'Chimborazo', 'Riobamba', 'ACTIVE', 'PUE-CM-EC-13313', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'CRTR1', 'CRTR', 'CHIMBORAZO|RIOBAMBA|TELCONETSA|RIOBAMBA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-1844', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-925', 'Portoviejo', 'Manabí', 'Portoviejo', 'ACTIVE', 'PUE-CM-EC-13322', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'MPTP1', 'MPTP', 'MANABI|PORTOVIEJO|TELCONETSA|PORTOVIEJO', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-1876', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-943', 'Telconet Edificio Loja', 'Loja', 'Loja', 'ACTIVE', 'PUE-CM-EC-13377', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'LLTT1', 'LLTT', 'LOJA|LOJA|TELCONETSA|TELCONETEDIFICIOLOJA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2861', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-448', 'Metro Parqueo', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-20007', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTMA1', 'PQTM', 'PICHINCHA|QUITO|TELCONETSA|METROPARQUEO', 'A', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2861', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-448', 'Metro Parqueo', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-7196', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTMA2', 'PQTM', 'PICHINCHA|QUITO|TELCONETSA|METROPARQUEO', 'A', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-6003', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-2538', 'El coca', 'Orellana', 'Francisco de Orellana (Coca)', 'ACTIVE', 'PUE-CM-EC-30132', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'OFTE1', 'OFTE', 'ORELLANA|FRANCISCODEORELLANACOCA|TELCONETSA|ELCOCA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2447', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-632', 'Ibarra', 'Imbabura', 'Ibarra', 'ACTIVE', 'PUE-CM-EC-17972', 'Guardiania', '12/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'IITI1', 'IITI', 'IMBABURA|IBARRA|TELCONETSA|IBARRA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2447', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-632', 'Ibarra', 'Imbabura', 'Ibarra', 'ACTIVE', 'PUE-CM-EC-9217', 'Guardiania', '24/7', 3.0, 'I', 'ACTIVE', '4-2', 6, 'IITI2', 'IITI', 'IMBABURA|IBARRA|TELCONETSA|IBARRA', '', 2),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-5277', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-2392', 'Casa Gerencia Ayangue', 'Santa Elena', 'Santa Elena', 'ACTIVE', 'PUE-CM-EC-28457', 'Guardiania', '24/7', 3.0, 'I', 'ACTIVE', '4-2', 6, 'SSTC1', 'SSTC', 'SANTAELENA|SANTAELENA|TELCONETSA|CASAGERENCIAAYANGUE', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-210', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-115', 'Armenia', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-1260', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTAA1', 'PQTA', 'PICHINCHA|QUITO|TELCONETSA|ARMENIA', 'A', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-212', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-121', 'Sucursal Manta', 'Manabí', 'Manta', 'ACTIVE', 'PUE-CM-EC-1263', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'MMTS1', 'MMTS', 'MANABI|MANTA|TELCONETSA|SUCURSALMANTA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-1375', 'Servicio de Vigilancia Armada de 24h de Lunes a Domingo', 'ACTIVE', 'PTO-CM-EC-692', 'Telconet Manta', 'Manabí', 'Manta', 'ACTIVE', 'PUE-CM-EC-9897', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'MMTT1', 'MMTT', 'MANABI|MANTA|TELCONETSA|TELCONETMANTA', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-1978', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-866', 'Telconet Tecnica Sucursal Manta - Monitoreo', 'Manabí', 'Manta', 'ACTIVE', 'PUE-CM-EC-13714', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'MMTTA1', 'MMTT', 'MANABI|MANTA|TELCONETSA|TELCONETTECNICASUCURSALMANTAMONITOREO', 'A', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-3661', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1807', 'Lago Agrio', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-23057', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTL1', 'PQTL', 'PICHINCHA|QUITO|TELCONETSA|LAGOAGRIO', '', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2044', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1041', 'Telconet Loja Garaje', 'Loja', 'Loja', 'ACTIVE', 'PUE-CM-EC-14387', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'LLTTA1', 'LLTT', 'LOJA|LOJA|TELCONETSA|TELCONETLOJAGARAJE', 'A', 1),
('CLI-CM-EC-61', 'Telconet S.A.', 'ACTIVE', 'SER-CM-EC-2157', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1131', 'Nodo Base Sur', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-16337', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQTN1', 'PQTN', 'PICHINCHA|QUITO|TELCONETSA|NODOBASESUR', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-5680', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-2490', 'Netlife Valle de los Chillos', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-29292', 'Guardiania', '8/1', 0.29, 'I', 'ACTIVE', '1-1', 2, 'PQMN1', 'PQMN', 'PICHINCHA|QUITO|MEGADATOSSA|NETLIFEVALLEDELOSCHILLOS', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-5680', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-2490', 'Netlife Valle de los Chillos', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-29291', 'Guardiania', '9/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'PQMN2', 'PQMN', 'PICHINCHA|QUITO|MEGADATOSSA|NETLIFEVALLEDELOSCHILLOS', '', 2),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-4796', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-2223', 'Netlife Edificio Vivanco Nuñez de Vela', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-26916', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQMNA1', 'PQMN', 'PICHINCHA|QUITO|MEGADATOSSA|NETLIFEEDIFICIOVIVANCONUNEZDEVELA', 'A', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-1361', 'Servicio de Vigilancia Armada de 10h Diurnas de Lunes a Viernes', 'ACTIVE', 'PTO-CM-EC-680', 'Netlife  Riobamba', 'Chimborazo', 'Riobamba', 'ACTIVE', 'PUE-CM-EC-9810', 'Guardiania', '10/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'CRMN1', 'CRMN', 'CHIMBORAZO|RIOBAMBA|MEGADATOSSA|NETLIFERIOBAMBA', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-1680', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-813', 'Netlife Ambato', 'Tungurahua', 'Ambato', 'ACTIVE', 'PUE-CM-EC-12306', 'Guardiania', '10/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'TAMN1', 'TAMN', 'TUNGURAHUA|AMBATO|MEGADATOSSA|NETLIFEAMBATO', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-1680', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-813', 'Netlife Ambato', 'Tungurahua', 'Ambato', 'ACTIVE', 'PUE-CM-EC-30170', 'Guardiania', '5/1', 0.29, 'I', 'ACTIVE', '1-1', 2, 'TAMN2', 'TAMN', 'TUNGURAHUA|AMBATO|MEGADATOSSA|NETLIFEAMBATO', '', 2),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-3377', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-736', 'Netlife Santo Domingo', 'Santo Domingo de los Tsáchilas', 'Santo Domingo', 'ACTIVE', 'PUE-CM-EC-22080', 'Guardiania', '10/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'SSMN1', 'SSMN', 'SANTODOMINGODELOSTSACHILAS|SANTODOMINGO|MEGADATOSSA|NETLIFESANTODOMINGO', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-3377', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-736', 'Netlife Santo Domingo', 'Santo Domingo de los Tsáchilas', 'Santo Domingo', 'ACTIVE', 'PUE-CM-EC-29733', 'Guardiania', '9/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'SSMN2', 'SSMN', 'SANTODOMINGODELOSTSACHILAS|SANTODOMINGO|MEGADATOSSA|NETLIFESANTODOMINGO', '', 2),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-1492', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-727', 'Netlife Machala', 'El Oro', 'Machala', 'ACTIVE', 'PUE-CM-EC-10378', 'Guardiania', '10/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'EMMN1', 'EMMN', 'ELORO|MACHALA|MEGADATOSSA|NETLIFEMACHALA', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-1502', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-735', 'Netlife Manta', 'Manabí', 'Manta', 'ACTIVE', 'PUE-CM-EC-10393', 'Guardiania', '10/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'MMMN1', 'MMMN', 'MANABI|MANTA|MEGADATOSSA|NETLIFEMANTA', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-1955', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-993', 'Quevedo - La Quadra', 'Los Ríos', 'Quevedo', 'ACTIVE', 'PUE-CM-EC-13655', 'Guardiania', '9/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'LQMQ1', 'LQMQ', 'LOSRIOS|QUEVEDO|MEGADATOSSA|QUEVEDOLAQUADRA', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-2081', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1069', 'Villaflora', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-14741', 'Guardiania', '10.5/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'PQMV1', 'PQMV', 'PICHINCHA|QUITO|MEGADATOSSA|VILLAFLORA', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-2081', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1069', 'Villaflora', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-14744', 'Guardiania', '8/1', 0.29, 'I', 'ACTIVE', '1-1', 2, 'PQMV2', 'PQMV', 'PICHINCHA|QUITO|MEGADATOSSA|VILLAFLORA', '', 2),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-2614', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1406', 'Netlife Gran Aki Solanda', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-18943', 'Guardiania', '11.5/5', 0.95, 'I', 'ACTIVE', '6-2', 8, 'PQMNB1', 'PQMN', 'PICHINCHA|QUITO|MEGADATOSSA|NETLIFEGRANAKISOLANDA', 'B', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-2614', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1406', 'Netlife Gran Aki Solanda', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-22947', 'Guardiania', '10/2', 1.0, 'I', 'ACTIVE', '2-5', 7, 'PQMNB2', 'PQMN', 'PICHINCHA|QUITO|MEGADATOSSA|NETLIFEGRANAKISOLANDA', 'B', 2),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-2845', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1495', 'Netlife Ibarra', 'Imbabura', 'Ibarra', 'ACTIVE', 'PUE-CM-EC-19943', 'Guardiania', '9/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'IIMN1', 'IIMN', 'IMBABURA|IBARRA|MEGADATOSSA|NETLIFEIBARRA', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-2845', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1495', 'Netlife Ibarra', 'Imbabura', 'Ibarra', 'ACTIVE', 'PUE-CM-EC-19944', 'Guardiania', '4/1', 0.29, 'I', 'ACTIVE', '1-1', 2, 'IIMN2', 'IIMN', 'IMBABURA|IBARRA|MEGADATOSSA|NETLIFEIBARRA', '', 2),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-3724', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1853', 'Neltife Salinas', 'Santa Elena', 'Salinas', 'ACTIVE', 'PUE-CM-EC-23687', 'Guardiania', '9/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'SSMNA1', 'SSMN', 'SANTAELENA|SALINAS|MEGADATOSSA|NELTIFESALINAS', 'A', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-552', 'Servicio de Vigilancia Armada de 10h Diurnas de Lunes a Viernes', 'ACTIVE', 'PTO-CM-EC-383', 'Netlife Cuenca', 'Azuay', 'Cuenca', 'ACTIVE', 'PUE-CM-EC-6484', 'Guardiania', '10.5/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'ACMN1', 'ACMN', 'AZUAY|CUENCA|MEGADATOSSA|NETLIFECUENCA', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-1321', 'Servicio de Vigilancia Armada de 10h Diurnas de Lunes a Viernes y 6h los Sábados diurnas', 'ACTIVE', 'PTO-CM-EC-659', 'Netlife Quevedo', 'Los Ríos', 'Quevedo', 'ACTIVE', 'PUE-CM-EC-9381', 'Guardiania', '10/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'LQMN1', 'LQMN', 'LOSRIOS|QUEVEDO|MEGADATOSSA|NETLIFEQUEVEDO', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-1321', 'Servicio de Vigilancia Armada de 10h Diurnas de Lunes a Viernes y 6h los Sábados diurnas', 'ACTIVE', 'PTO-CM-EC-659', 'Netlife Quevedo', 'Los Ríos', 'Quevedo', 'ACTIVE', 'PUE-CM-EC-9382', 'Guardiania', '6/1', 0.29, 'I', 'ACTIVE', '1-1', 2, 'LQMN2', 'LQMN', 'LOSRIOS|QUEVEDO|MEGADATOSSA|NETLIFEQUEVEDO', '', 2),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-2084', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1070', 'Neltlife Bodega', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-14745', 'Guardiania', '24/7', 2.67, 'I', 'ACTIVE', '6-2', 8, 'PQMNC1', 'PQMN', 'PICHINCHA|QUITO|MEGADATOSSA|NELTLIFEBODEGA', 'C', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-2605', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1401', 'Netlife Portoviejo', 'Manabí', 'Portoviejo', 'ACTIVE', 'PUE-CM-EC-18909', 'Guardiania', '9/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'MPMN1', 'MPMN', 'MANABI|PORTOVIEJO|MEGADATOSSA|NETLIFEPORTOVIEJO', '', 1),
('CLI-CM-EC-36', 'Megadatos S.A.', 'ACTIVE', 'SER-CM-EC-3045', 'Servicio de Vigilancia Armada Fija', 'ACTIVE', 'PTO-CM-EC-1566', 'Netlife Torre del Puente', 'Pichincha', 'Quito', 'ACTIVE', 'PUE-CM-EC-20884', 'Guardiania', '10/5', 1.0, 'I', 'ACTIVE', '5-2', 7, 'PQMND1', 'PQMN', 'PICHINCHA|QUITO|MEGADATOSSA|NETLIFETORREDELPUENTE', 'D', 1);

DO $$
DECLARE
  v_instance uuid := current_setting('app.import.instance_country_id')::uuid;
  r record;
  v_client_id uuid;
  v_service_id uuid;
  v_point_id uuid;
  v_existing_service_id uuid;
  v_post_id uuid;
BEGIN
  IF EXISTS (SELECT 1 FROM post p JOIN import_data_prueba i ON i.post_code2=p.code2
             WHERE p.instance_country_id=v_instance) THEN
    RAISE EXCEPTION 'La instancia ya contiene uno o más post.code2 del archivo; este SQL es una carga inicial y no debe mezclarse con datos existentes.';
  END IF;
  IF EXISTS (SELECT 1 FROM post p JOIN import_data_prueba i ON i.post_code=p.code
             WHERE p.instance_country_id=v_instance) THEN
    RAISE EXCEPTION 'Existe una colisión con un post.code generado; use una instancia vacía o el API SIC:COM.';
  END IF;
  IF EXISTS (SELECT 1 FROM post_code_allocation a JOIN import_data_prueba i
             ON i.base_code=a.base_code AND i.source_signature=a.source_signature
             WHERE a.instance_country_id=v_instance) THEN
    RAISE EXCEPTION 'Ya existe una asignación de código de puesto para el archivo; use una instancia vacía o el API SIC:COM.';
  END IF;

  FOR r IN SELECT DISTINCT ON (client_code) * FROM import_data_prueba ORDER BY client_code LOOP
    INSERT INTO client(id,instance_country_id,code,name,commercial_status,source_system,source_version,created_at,updated_at)
    VALUES(gen_random_uuid(),v_instance,r.client_code,r.client_name,r.client_status,'SIC_COM','DATA-PRUEBA-v1',now(),now())
    ON CONFLICT (instance_country_id,code) DO UPDATE SET name=EXCLUDED.name, commercial_status=EXCLUDED.commercial_status,
      source_system=EXCLUDED.source_system, source_version=EXCLUDED.source_version, updated_at=now()
    RETURNING id INTO v_client_id;
  END LOOP;

  FOR r IN SELECT DISTINCT ON (service_code) * FROM import_data_prueba ORDER BY service_code LOOP
    SELECT id INTO v_client_id FROM client WHERE instance_country_id=v_instance AND code=r.client_code;
    INSERT INTO service(id,instance_country_id,code,name,client_name,client_id,commercial_status,config_status,source_system,source_version,created_at,updated_at)
    VALUES(gen_random_uuid(),v_instance,r.service_code,r.service_name,r.client_name,v_client_id,r.service_status,'TO_CONFIGURE','SIC_COM','DATA-PRUEBA-v1',now(),now())
    ON CONFLICT (instance_country_id,code) DO UPDATE SET name=EXCLUDED.name, client_name=EXCLUDED.client_name,
      client_id=EXCLUDED.client_id, commercial_status=EXCLUDED.commercial_status, source_system=EXCLUDED.source_system,
      source_version=EXCLUDED.source_version, updated_at=now()
    RETURNING id INTO v_service_id;
  END LOOP;

  FOR r IN SELECT DISTINCT ON (point_code) * FROM import_data_prueba ORDER BY point_code LOOP
    SELECT id INTO v_service_id FROM service WHERE instance_country_id=v_instance AND code=r.service_code;
    SELECT id,service_id INTO v_point_id,v_existing_service_id FROM point WHERE instance_country_id=v_instance AND code=r.point_code;
    IF FOUND AND v_existing_service_id<>v_service_id THEN
      RAISE EXCEPTION 'El Punto % ya pertenece a otro Servicio en la instancia.',r.point_code;
    END IF;
    INSERT INTO point(id,instance_country_id,service_id,code,name,province,city,client_name,company_id,status,operational_assignment_status,received_from_sic_com_at,created_at,updated_at)
    VALUES(gen_random_uuid(),v_instance,v_service_id,r.point_code,r.point_name,r.province,r.city,r.client_name,NULL,r.point_status,'PENDING',now(),now(),now())
    ON CONFLICT (instance_country_id,code) DO UPDATE SET name=EXCLUDED.name, province=EXCLUDED.province, city=EXCLUDED.city,
      client_name=EXCLUDED.client_name, status=EXCLUDED.status, received_from_sic_com_at=now(), updated_at=now()
    RETURNING id INTO v_point_id;
  END LOOP;

  FOR r IN SELECT * FROM import_data_prueba ORDER BY point_code,post_code2 LOOP
    SELECT id INTO v_point_id FROM point WHERE instance_country_id=v_instance AND code=r.point_code;
    INSERT INTO post(id,instance_country_id,point_id,code,code2,name,format,fhe,tier,commercial_status,config_status,created_at,updated_at)
    VALUES(gen_random_uuid(),v_instance,v_point_id,r.post_code,r.post_code2,r.post_name,r.post_format,r.fhe,r.tier,r.post_status,'TO_CONFIGURE',now(),now())
    RETURNING id INTO v_post_id;

    INSERT INTO post_planning_cycle_snapshot(id,instance_country_id,post_id,rotation_code,cycle_length_days,source_system,source_version,created_at,updated_at)
    VALUES(gen_random_uuid(),v_instance,v_post_id,r.rotation_code,r.cycle_length_days,'SIC_COM','DATA-PRUEBA-v1',now(),now());

    INSERT INTO post_shift_template(id,instance_country_id,post_id,shift_code,shift_name,start_time,end_time,day_mask,active,commercial_version,created_at,updated_at)
    VALUES
      (gen_random_uuid(),v_instance,v_post_id,'D08','Diurno','06:00','14:00',127,true,'DATA-PRUEBA-v1',now(),now()),
      (gen_random_uuid(),v_instance,v_post_id,'T08','Tarde','14:00','22:00',127,true,'DATA-PRUEBA-v1',now(),now()),
      (gen_random_uuid(),v_instance,v_post_id,'N08','Nocturno','22:00','06:00',127,true,'DATA-PRUEBA-v1',now(),now());
  END LOOP;

  FOR r IN SELECT base_code,source_signature,discriminator,max(post_sequence) AS last_sequence
           FROM import_data_prueba GROUP BY base_code,source_signature,discriminator LOOP
    INSERT INTO post_code_allocation(id,instance_country_id,base_code,source_signature,discriminator,last_sequence,created_at,updated_at)
    VALUES(gen_random_uuid(),v_instance,r.base_code,r.source_signature,r.discriminator,r.last_sequence,now(),now());
  END LOOP;
END $$;

COMMIT;
