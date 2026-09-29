-- SGI: Comando | Flyway V31 quarantine tombstone
-- P0 safety correction 2026-09-27.
--
-- The original V31 embedded a 200-employee UAT fixture and MUST NOT run
-- automatically as a schema migration. Its exact historical SQL is preserved at:
--   database/uat-fixtures/legacy-original-flyway/V31__insertar_200_empleados_con_avatares.sql
-- and the explicit UAT fixture is:
--   database/uat-fixtures/DME_02_insertar_200_empleados_con_avatares_UAT.sql
--
-- Fresh databases safely record V31 as applied without inserting fixture data.
-- Existing databases that already applied the former V31 will have a checksum
-- mismatch: follow docs/MIGRATION_SAFETY_V30_V31.md before starting this RC.
-- DME | SGI:Comando | Carga de 200 empleados con avatares desde empleados.xlsx
-- Fecha: 2026-09-24
-- Origen: Hoja1, columnas id, nombre y cargo o rol.
-- Alcance: Instancia-País Ecuador (11111111-1111-1111-1111-111111111111).
-- Prerrequisito: ejecutar DME_01_eliminar_todos_los_empleados.txt.
-- Distribución determinística y equilibrada entre las seis compañías actuales,
-- ordenadas por código. Los campos ID/SMC y turno permanecen NULL.
-- photo_key reutiliza los 12 avatares ficticios UAT; no son fotografías reales.
-- El Excel no incluye sexo/género. Se infiere solo para ocho nombres inequívocos:
-- Katherine, Lady, Hillary, Claudia, Madeleine, Brithany, Clara y Cinthia usan
-- avatares femeninos UAT. Los demás usan avatares masculinos UAT. Pueden repetirse.

BEGIN;

LOCK TABLE
    employee_operational_snapshot,
    employee_skill_snapshot,
    employee_unavailability_snapshot,
    employee_company_transfer,
    company_membership,
    company
IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE dme_empleados_carga (
    row_number integer PRIMARY KEY,
    persona_id bigint NOT NULL UNIQUE,
    employee_id uuid NOT NULL UNIQUE,
    full_name text NOT NULL,
    role_code text NOT NULL
) ON COMMIT DROP;

INSERT INTO dme_empleados_carga
    (row_number, persona_id, employee_id, full_name, role_code)
VALUES
    (1, 310, 'ce9020ec-ccf3-354b-9eb8-98ef1b6168c2'::uuid, 'Segundo Oswaldo Perez Muñoz', 'Responsable de Punto'),
    (2, 316, 'e3767f8f-7ec7-3ca5-a765-868b975372a8'::uuid, 'Patrik Nivo Molina Verdezoto', 'Supervisor de Seguridad'),
    (3, 363, '5bd6646d-416c-3107-862e-dc379fad83eb'::uuid, 'Pedro Javier Solis Angel', 'Responsable de Punto'),
    (4, 397, 'df073ba5-472f-3db5-a137-5f6edadf6ef3'::uuid, 'Wilton Leonel Cedeño Veliz', 'Responsable de Punto'),
    (5, 554, '9f9eb0a0-80f7-316e-8bfc-8475a85d67c5'::uuid, 'Douglas Yofre Lopez Mora', 'Supervisor de Seguridad'),
    (6, 555, '0840feb4-6f7f-3b19-9860-a4d76e94077e'::uuid, 'Jonathan Rodolfo Lopez Ramirez', 'Coordinador de Compañia'),
    (7, 577, 'ac6ed1b6-f87c-3ffe-baf1-9634ed99d9ee'::uuid, 'Francisco Alejandro Peñaherrera Delgado', 'Coordinador de Compañia'),
    (8, 604, 'b0db0eed-782b-3fd0-89a8-d690b4bf65d6'::uuid, 'Diego Fernando Argoti Pozo', 'Coordinador de Compañia'),
    (9, 610, '0179e953-9d5b-37c9-9447-0c382cf7f3a6'::uuid, 'Nelson Daniel Ponce', 'Jefe de Operaciones'),
    (10, 1270, '06fab0e6-f743-38ae-883e-79fce46c55a0'::uuid, 'Jonathan Ramon Saltos Intriago', 'Supervisor de Seguridad'),
    (11, 1292, '9eaf1ae8-fbbb-38a8-a9cd-e120e94cbfff'::uuid, 'Cesar Euclides Quinto Caiza', 'Escolta Líder JT'),
    (12, 1295, '7816ba53-80d6-3949-88e2-f985790c4f1c'::uuid, 'Miguel Angel Peñafiel Miranda', 'Escolta Líder'),
    (13, 2149, 'a84b77bb-20e9-33e1-af88-4974151f2cf2'::uuid, 'Luis Eduardo Vega Ronquillo', 'Supervisor de Seguridad'),
    (14, 2328, '989aefe2-9247-3d57-8e43-7c688fa5afeb'::uuid, 'Luis Victor Constante Mora', 'Supervisor de Seguridad'),
    (15, 2925, 'fe1b5f3f-3043-34f4-ba56-1ac8b35cc857'::uuid, 'Juan Carlos Miguez Lucio', 'Supervisor de Seguridad'),
    (16, 3014, 'df0903d4-7f2c-3686-9324-60bd5fc703db'::uuid, 'Tomas Andres Conforme Valero', 'Supervisor de Seguridad'),
    (17, 3219, 'ee7def88-ed61-3d3f-9b95-8e1ff9c8d625'::uuid, 'Pablo Cesar Del Valle Carlos', 'Supervisor de Seguridad CL'),
    (18, 3559, 'e8de1bdc-580f-3519-93f1-5219152a8f7b'::uuid, 'Welinton Eduardo Casalombo Pozo', 'Supervisor de Seguridad'),
    (19, 3655, 'cbc83c4d-d33b-3a2e-bd37-534bcfcbbda2'::uuid, 'Jhonny Rafael Tenorio Caicedo', 'Escolta Líder'),
    (20, 3667, '847388ce-695f-3558-8add-39b4787111fa'::uuid, 'Antonio  Oviedo Martinez', 'Escolta Líder'),
    (21, 4023, 'eb7e9792-1f47-3fad-a326-8df6a650774d'::uuid, 'Alex Guillermo Zambrano Valero', 'Escolta Líder'),
    (22, 4320, 'f2902e88-a901-3263-a410-339ae6623c65'::uuid, 'Mauricio Alejandro Ortega Rojas', 'Escolta Líder'),
    (23, 4379, 'eda979fc-4d9d-3b34-b9f9-a909f4bf2bab'::uuid, 'Angel Eduardo Moreira Zambrano', 'Supervisor de Seguridad'),
    (24, 4717, 'b6d60722-14fd-3180-9416-37a8983e032c'::uuid, 'Tyrone Stuar Naula Briones', 'Supervisor de Seguridad'),
    (25, 4790, 'bec454ec-3bd2-3004-bdea-9f304c749539'::uuid, 'Bryan Ernesto Chica Ramirez', 'Supervisor de Seguridad'),
    (26, 4806, '11a5b5af-432c-31f8-a754-13ef0da2ac08'::uuid, 'Miguel Napoleón Enríquez Cabrera', 'Escolta Líder'),
    (27, 5107, '1b45e20a-6e1b-31a6-9929-a0c3cb3473d4'::uuid, 'Richard Leonardo Cordova Vera', 'Escolta Líder'),
    (28, 5418, '85e863eb-e403-3d91-a52d-c4efb50d96c4'::uuid, 'Deivin Jesus Vega Toaza', 'Supervisor de Seguridad'),
    (29, 5806, 'b0fa7ba2-4360-302c-8a23-449dafb18b79'::uuid, 'Jose Enrrique Yumbla Guerrero', 'Coordinador de Compañia'),
    (30, 6504, '843b7660-05a9-3890-af6b-7d659ab4b20b'::uuid, 'Danny Daniel Merejildo Tomala', 'Responsable de Punto'),
    (31, 6559, 'd389e452-fe65-3a1c-ad06-ed65f4084fb3'::uuid, 'Jose Andres Hoyos Olivo', 'Escolta Líder'),
    (32, 6952, 'e1ab2438-3344-3f79-bc91-b9a1cf5261e9'::uuid, 'Dennis Alfredo Reyes Tobar', 'Supervisor de Seguridad'),
    (33, 6961, '85174efb-e43f-3a8c-b36d-4a498fdcc6de'::uuid, 'Ronaldo Sebastián Cabrera Delgado', 'Supervisor de Seguridad'),
    (34, 7150, 'a45fc152-58c9-35ee-a875-7438e4a37b9c'::uuid, 'Freddy Armando Luces Cedillo', 'Supervisor de Seguridad CL'),
    (35, 7253, '14f6b4c5-98a2-3757-9df0-0dd2f7882df1'::uuid, 'Segundo Leonidas Alajo Guanoluisa', 'Supervisor de Seguridad CL'),
    (36, 7408, '20d1291c-944d-3692-b2e2-8a85b0a804e7'::uuid, 'Jose Jacinto Pamila Vera', 'Supervisor de Seguridad CL'),
    (37, 7664, '1f498d08-4a38-366c-ab22-ce55e33547e5'::uuid, 'Angel Mauricio Oyola Jimenez', 'Director Nacional de Operaciones'),
    (38, 8031, '36689300-460b-36d5-a777-f41514aa3bcd'::uuid, 'Juan Jose Leon Patiño', 'Supervisor de Seguridad'),
    (39, 8034, 'f3de5ccc-0a3f-3076-89af-b5cc77afef44'::uuid, 'Katherine Yurelis Ortiz Solis', 'Supervisor de Seguridad'),
    (40, 8079, 'ad4a743c-3b87-3399-b170-2376b04b86d4'::uuid, 'Segundo Manuel Ganchozo Vera', 'Supervisor de Seguridad CL'),
    (41, 8116, '86172a58-61dc-320c-8bbe-f777741a3658'::uuid, 'Steven Javier Leyton Baque', 'Escolta Líder'),
    (42, 8226, '8470ce47-4bac-354c-9224-ad5c2a43dadf'::uuid, 'Byron Steven Gallegos Zhigue', 'Supervisor de Seguridad'),
    (43, 8327, '9c7bf25e-b3f3-3672-8f60-97c7c0a6f1f9'::uuid, 'Jose Luis Rojas Merelo', 'Supervisor de Seguridad'),
    (44, 8615, '1057fe4d-04e1-3bcb-95b2-f326cd2c6556'::uuid, 'Joel Steven Arreaga Macias', 'Supervisor de Seguridad'),
    (45, 8725, 'a05249a2-83a5-351d-8d79-3d77d0542ab1'::uuid, 'Alex Eduardo Quishpe Mena', 'Supervisor de Seguridad'),
    (46, 8816, '19f38e66-ad7a-3220-bc1b-c9fa4a3031f3'::uuid, 'Jefferson Gabriel Cedeño Sanchez', 'Supervisor de Seguridad'),
    (47, 8926, '8a980003-ddfc-36ce-ac15-d3a8b4590fd7'::uuid, 'Wellington Steve Alavera Piguave', 'Supervisor de Seguridad'),
    (48, 9158, '321c8de4-c623-38f0-a629-1bcc74d1cbdd'::uuid, 'Andres Alejandro Del Salto Holguin', 'Coordinador de Compañía CL'),
    (49, 9225, '3ea49dd3-3d0f-3f68-80f8-651656010721'::uuid, 'David Samuel Yunga Leon', 'Coordinador de Compañía CL'),
    (50, 9236, '1954ea31-8533-343c-ba76-ad4941a2b48f'::uuid, 'Anthoni Steven Pérez Castro', 'Supervisor de Seguridad'),
    (51, 9259, '7f3f6b11-1020-30fb-a045-eb4191b9dcc4'::uuid, 'Jorge Orlando Rosales Leones', 'Escolta Líder'),
    (52, 9260, '24da9ae2-8d1b-306b-9356-7dc589864e7b'::uuid, 'Manuel Antonio Coronel Herrera', 'Supervisor de Seguridad'),
    (53, 9268, '354b8a9d-3ca5-3473-9566-26c5358f22a0'::uuid, 'Carlos moises Robles holguin', 'Supervisor de Seguridad'),
    (54, 9273, '21a29e35-b286-39af-94f5-45cab52fa02c'::uuid, 'Marco Antonio Cabrera Sanchez', 'Supervisor de Seguridad'),
    (55, 9310, '09376993-1e0c-358f-aabe-4157a083a168'::uuid, 'Anthony Joel Avilez Icaza', 'Supervisor de Seguridad'),
    (56, 9451, '85222879-5f38-3fe8-acd9-ab1de1fbbf93'::uuid, 'Samuel Augusto Cerrud corneli', 'Supervisor de Seguridad CL'),
    (57, 9456, '039f464a-d1f2-3670-811f-4650120f0455'::uuid, 'Jefferson Rolando Idrovo Miranda', 'Escolta Líder'),
    (58, 9691, '4c61144c-5861-38e4-bd97-05f85a51fec9'::uuid, 'Rogelio Efrain Ortiz Jacome', 'Supervisor de Seguridad'),
    (59, 3147, '74a285eb-206e-3d43-b11c-e42d5c77edab'::uuid, 'Juan Carlos Pacha Pacha', 'Agente de Consola de Monitoreo Senior'),
    (60, 2300, '6335a6a0-f5e0-3548-a3a0-8f4e8b92a327'::uuid, 'Danilo Raul Villafuerte Perez', 'Agente de Consola de Monitoreo Senior'),
    (61, 6387, 'bd500da0-cc58-31ac-948d-0d5de1895f17'::uuid, 'Jefferson Geovanny Espinoza Maldonado', 'Agente de Consola de Monitoreo Senior'),
    (62, 3435, 'be795392-9c42-3c04-b47f-de8e55c4e0d5'::uuid, 'Jeremias Abel Valverde Benavides', 'Agente de Consola de Monitoreo Senior'),
    (63, 2050, '39301ee1-32db-32c6-b200-827c928a705d'::uuid, 'Francisco Javier Calle Garcia', 'Agente de Seguridad'),
    (64, 9763, '144df352-4c53-3dd8-85b8-acf155a3b649'::uuid, 'Italo Geovanny Piedra Godoy', 'Agente de Seguridad'),
    (65, 9639, '0dce31d1-8db5-3acf-b962-a364f84d8b3e'::uuid, 'Denis Alexander Bravo Zambrano', 'Agente de Seguridad'),
    (66, 9637, 'ec0b69d8-89b0-3446-a87a-a5a342cfb563'::uuid, 'Magno Stalyn Vera Vera', 'Agente de Seguridad'),
    (67, 9636, 'fcf488ea-9c43-3a1d-9494-a5c8a4145fa3'::uuid, 'Alex Enrique Chiriboga Mafla', 'Agente de Seguridad'),
    (68, 9635, '42efa24c-4e8b-30ca-b78f-d833eb4f4c3b'::uuid, 'Ruben Ezequiel Barahona Navarrete', 'Agente de Seguridad'),
    (69, 9634, 'e58ce411-5823-33fc-a52c-b07ff22cccb0'::uuid, 'Josue Abelardo Bonilla Chilan', 'Agente de Seguridad'),
    (70, 9633, 'df033df4-1fb9-3a31-b48d-8c1d6dae13f3'::uuid, 'José Miguel Cerda Yumbo', 'Agente de Seguridad'),
    (71, 9632, 'b1655d43-7b2e-3b18-b2be-6bd541680d59'::uuid, 'Medardo Miguel Andy Grefa', 'Agente de Seguridad'),
    (72, 9630, '4777ee75-ba74-32c1-ac0f-72b8d349d8f7'::uuid, 'Victor Diego Montalvan Rivera', 'Agente de Seguridad'),
    (73, 9629, '027a0ea9-9703-3680-9f4d-a7dc775854e6'::uuid, 'Nerys Antonio Murillo Mejia', 'Agente de Seguridad'),
    (74, 9626, 'c9134fa4-8c2d-3a61-b40b-cb39cf0fa9eb'::uuid, 'Roberto Carlos Arboleda Chichande', 'Agente de Seguridad'),
    (75, 9625, 'da5f6242-e3f3-354c-b75a-7422676fff18'::uuid, 'Christian Jonathan Orellana Paz', 'Agente de Seguridad'),
    (76, 9624, 'fd5c1b0a-70eb-3207-8499-337bdd99ed2c'::uuid, 'Esteban Isaias Valiente Ayala', 'Agente de Seguridad'),
    (77, 9623, 'bebaeff0-d0d6-3dc2-984a-392c6ba9d87f'::uuid, 'Fernando Andres Coronel Castillo', 'Agente de Seguridad'),
    (78, 9616, 'd72a587c-ba2d-38a4-a51a-4ef137f8b5fe'::uuid, 'Mauricio Eugenio Montenegro Vera', 'Agente de Seguridad'),
    (79, 9613, '8c7acc33-7b5a-31b5-8916-ad16242617ba'::uuid, 'Rusell Harrison Banchon Ramirez', 'Agente de Seguridad'),
    (80, 9612, 'f615410e-2af8-3a03-8843-835e7f5ee701'::uuid, 'Luigi Antonio Alexandre Segura', 'Agente de Seguridad'),
    (81, 9611, '9c2e82f7-40dd-3b2e-af52-34b56fbd4001'::uuid, 'Jorge Andres Muñoz Gonzalez', 'Agente de Seguridad'),
    (82, 9610, '37b21681-34bc-3da5-ae4a-1723f4d6d27f'::uuid, 'Lady Margarita Honores Vega', 'Agente de Seguridad'),
    (83, 9609, '9fb0a5e2-0491-3c43-978e-94712d7bb982'::uuid, 'Leiner Alexander Vera delgado', 'Agente de Seguridad'),
    (84, 9607, '2ecf8aa8-3f21-3250-a839-180da8835c9d'::uuid, 'Patricio Guillermo Olivo Espinoza', 'Agente de Seguridad'),
    (85, 9606, '7ddfee88-23e7-3773-bb8a-0ba56815c570'::uuid, 'Cristhian Andrés Pinargote Zambrano', 'Agente de Seguridad'),
    (86, 9605, 'd34f521b-cc90-313f-a9cb-68f6d9ce98dd'::uuid, 'Wilfrido Geovanny Pilalo Mora', 'Agente de Seguridad'),
    (87, 9603, '34f755ee-182e-3f33-adef-ab3d1679abc5'::uuid, 'Darwin Rafael Quijije Rosado', 'Agente de Seguridad'),
    (88, 9602, '9fbe87d5-d297-3261-8310-d9beb4c0c353'::uuid, 'Luis Enrique Andrade Veliz', 'Agente de Seguridad'),
    (89, 9601, '87134ac9-19ec-38b4-9a12-f09881144c13'::uuid, 'Victor Luis Rodriguez villamar', 'Agente de Seguridad'),
    (90, 9600, 'f55c9091-74ef-30f9-87bf-dbbd69d6a40b'::uuid, 'Roberto Michael Rodríguez Quintero', 'Agente de Seguridad'),
    (91, 9599, '54eca46f-260d-3611-869b-55c95533963f'::uuid, 'Alexis Joel Triana Magallanes', 'Agente de Seguridad'),
    (92, 9598, '465d7bce-f457-3c5a-8c7a-04e102df3c48'::uuid, 'Edwin Adrian Peralta Alvarez', 'Agente de Seguridad'),
    (93, 9597, '98272059-b964-34f4-8338-d0114da5eb47'::uuid, 'Diego Alex Gonzalez Yagual', 'Agente de Seguridad'),
    (94, 9596, '092841a8-7d22-3f2a-ba91-c802983d0bc5'::uuid, 'Jorge Andres Empuño Monserrate', 'Agente de Seguridad'),
    (95, 9595, '9f55f426-8af7-3398-9d27-5c7e2f5be14f'::uuid, 'Axel Fernando Limones Zambrano', 'Agente de Seguridad'),
    (96, 9593, '65742efc-c76c-300e-a697-c3a21946bad3'::uuid, 'Cesar Stalyn Bravo Mantuano', 'Agente de Seguridad'),
    (97, 9591, 'fb54eeab-5585-32f6-9a1c-4d0df947d814'::uuid, 'David Tito Moran Aguirre', 'Agente de Seguridad'),
    (98, 9588, 'e2571d7c-3b62-3bf3-a295-b7ad8313e9aa'::uuid, 'William Daniel Bonilla De La Rosa', 'Agente de Seguridad'),
    (99, 9584, '51a02118-f4ec-3ae1-a76c-8d244d9f46c6'::uuid, 'Emmanuel Josue Moncada Florez', 'Agente de Seguridad'),
    (100, 9582, '37379dc6-9428-3cab-b9b6-6f4aba427904'::uuid, 'Hillary Vianey Villa Mero', 'Agente de Seguridad'),
    (101, 9577, 'cf4321be-1620-3a10-a09f-32f9dac53ea8'::uuid, 'Kevin Alexander Vera Valencia', 'Agente de Seguridad'),
    (102, 9576, 'fd92102d-439b-3cd9-b792-eaf2becb4727'::uuid, 'Cristian Andrés Peralta Morante', 'Agente de Seguridad'),
    (103, 9573, '11f664a5-18b1-38ff-895c-4fb1872e69e2'::uuid, 'CLAUDIA PATRICIA SALAS CONTRERAS', 'Agente de Seguridad'),
    (104, 9572, '860510fb-a34d-378b-ac44-e39975e4f641'::uuid, 'Vismar Alexis Cedeño Parrales', 'Agente de Seguridad'),
    (105, 9566, '2c44af89-c939-3857-9357-085e45529ee5'::uuid, 'Manuel Leonardo Verduga Avila', 'Agente de Seguridad'),
    (106, 9564, 'c7090de0-f244-3814-b3bf-60d33c7bec3f'::uuid, 'Jairo Brayan Tamayo Quishpe', 'Agente de Seguridad'),
    (107, 9562, '779e7774-2353-34c2-919f-d24aeab7444c'::uuid, 'German Hernan Nazareno Valverde', 'Agente de Seguridad'),
    (108, 9561, '29fa1846-438a-37d1-a181-2735f3c3a8e0'::uuid, 'Marlon Jose Mariscal Gonzabay', 'Agente de Seguridad'),
    (109, 9560, 'cda57a1d-5ed9-3f8d-ab50-1395871b297e'::uuid, 'Roberto Carlos Enderica Santillan', 'Agente de Seguridad'),
    (110, 9559, 'cc62c210-dbd8-3ba0-b5c4-a235ed2da34f'::uuid, 'Miguel Felipe Rivera Rivas', 'Agente de Seguridad'),
    (111, 9557, 'f030fdf8-c089-396a-886f-763f48956e53'::uuid, 'Madeleine  Calvo Torres', 'Agente de Seguridad'),
    (112, 9553, 'c5b48b5c-66bb-3178-9688-6bcc8a9b44c7'::uuid, 'Aaron Gabriel Ferreira Murillo', 'Agente de Seguridad'),
    (113, 9552, 'ab692ffc-3c3d-3b4a-932e-4f151c25e98e'::uuid, 'Juan Carlos Segarra Quispe', 'Agente de Seguridad'),
    (114, 9550, '1e2b7b02-05f3-3443-8999-8c2be48fb325'::uuid, 'Diego Alexander Navarro Flores', 'Agente de Seguridad'),
    (115, 9549, '3ce23404-8809-35bb-96b1-458474aece95'::uuid, 'Jandry Leonel Caicedo Triana', 'Agente de Seguridad'),
    (116, 9548, '89594c49-6cec-35a6-ae04-e3fcdea3d728'::uuid, 'Brithany Leonela Caicedo Angulo', 'Agente de Seguridad'),
    (117, 9546, '8a1b310e-9f95-3e77-bf94-5f7c5773af0c'::uuid, 'Jean Carlos Miranda Plaza', 'Agente de Seguridad'),
    (118, 9544, '43765107-139d-352d-a182-f51324512ccf'::uuid, 'Gilmar Joshua Pita Torres', 'Agente de Seguridad'),
    (119, 9543, 'a60f5e5b-6ef4-3800-b99e-f2fee1de396d'::uuid, 'Julio Javier Plaza Chila', 'Agente de Seguridad'),
    (120, 9542, '41c44742-f151-37a9-8118-48a97062bfd1'::uuid, 'Luis Ariel Litardo Martinez', 'Agente de Seguridad'),
    (121, 9541, '3e7127e7-cc0d-338b-84d1-e937b793afe2'::uuid, 'Kleber Alexi Holguin Holguin', 'Agente de Seguridad'),
    (122, 9540, 'e55c778c-4ff9-3557-874e-be4840153100'::uuid, 'Carlos Josue Romero Herrera', 'Agente de Seguridad'),
    (123, 9539, '7ca42683-9901-37d3-8178-9a1ca0f1a657'::uuid, 'Josias Emanuel Garcia Segura', 'Agente de Seguridad'),
    (124, 9537, '8393cc7d-4870-3b00-977e-53fc96c1e222'::uuid, 'Jordan Stuard Guerrero Gomez', 'Agente de Seguridad'),
    (125, 9536, '32796ab0-fd89-3653-a30b-993667a0a09f'::uuid, 'Livingthon Manuel Castro Lozano', 'Agente de Seguridad'),
    (126, 9535, '18ebc7b9-34c5-3b13-a879-7875fbdd6bb8'::uuid, 'Danner Fabián Alverca Cueva', 'Agente de Seguridad'),
    (127, 9533, 'e5305263-9d34-39a5-ba30-a16300b369bb'::uuid, 'Axel Mateo Parrales Gutiérrez', 'Agente de Seguridad'),
    (128, 9531, '7ffcb494-caa0-3553-afca-80eaf12897fc'::uuid, 'Ramon Antonio Chancay Mantuano', 'Agente de Seguridad'),
    (129, 9530, 'ffddaa88-22d0-306c-9672-dd14b0a14741'::uuid, 'Antonny Manuel Salvatierra Soledispa', 'Agente de Seguridad'),
    (130, 9529, 'dcb6036a-0023-32e2-b358-44f4497cc86f'::uuid, 'Francis Ronald Navarrete Sellan', 'Agente de Seguridad'),
    (131, 9528, 'bedff99b-0406-334c-913e-97f734cce95a'::uuid, 'Mateo Sebastián Vera Andrade', 'Agente de Seguridad'),
    (132, 9527, '2fcc8d59-93a9-34de-a0b6-b48bb4e713c5'::uuid, 'Elias Gedeon Mosquera Catagua', 'Agente de Seguridad'),
    (133, 9526, '670157a5-5adc-37a9-8edf-d8dd668c731a'::uuid, 'Jhon Byron Rodriguez Borja', 'Agente de Seguridad'),
    (134, 9523, 'daa4aee1-9896-323c-8e85-14b4bc10c59f'::uuid, 'Jefrey Jandry Valencia Ontaneda', 'Agente de Seguridad'),
    (135, 9522, '85e06390-f9e2-3ae3-b4cb-4020122e0372'::uuid, 'Stalyn Adrian Tomala Medina', 'Agente de Seguridad'),
    (136, 9521, '7b8c21a1-ef46-39c6-9de0-6f8a1d210a9c'::uuid, 'Michael Roddy Bazurto Suárez', 'Agente de Seguridad'),
    (137, 9519, 'bddf3c49-674f-3edc-85f6-94bf05a719f5'::uuid, 'Víctor Daniel Palacios Guagua', 'Agente de Seguridad'),
    (138, 9518, '65eb603f-7cd5-3de2-a732-3afc8d30c303'::uuid, 'Carlos Luis Cedeño León', 'Agente de Seguridad'),
    (139, 9517, 'd80b5da0-9d79-3a4f-bf20-18a5f3528c43'::uuid, 'Jeamphier Enrique Rosado Liberio', 'Agente de Seguridad'),
    (140, 9516, '3c411f74-82c2-308f-82fc-b132eaa6b617'::uuid, 'Vicente Javier Velasquez Salvatierra', 'Agente de Seguridad'),
    (141, 9515, '46a1ee8c-3990-3c9e-9443-cdc8f4bdc49c'::uuid, 'Angel Fernando Alvarado Cordova', 'Agente de Seguridad'),
    (142, 9514, '3136ee7f-9daa-3316-82fe-3236b2cb050b'::uuid, 'Edison Bacilio Mina Tenorio', 'Agente de Seguridad'),
    (143, 9513, 'a5eb24bb-17db-3801-8ad5-1e7703183f22'::uuid, 'Michael Freddy Avila Zambrano', 'Agente de Seguridad'),
    (144, 9512, '709512d8-cb3f-3c3a-a2bc-a25655a640b8'::uuid, 'Roger Alexander Castillo Ramos', 'Agente de Seguridad'),
    (145, 9508, '6e7a2448-bbfa-3345-bbd2-2b291a8b45a2'::uuid, 'Clara Yulexi Gaibor Aviles', 'Agente de Seguridad'),
    (146, 9507, '65506dea-efab-3dfe-85ba-31f045ba60f6'::uuid, 'Alexis Daniel Cedeño Navas', 'Agente de Seguridad'),
    (147, 9506, '0df0ed5d-d178-3896-805e-16a39bbb1356'::uuid, 'Darlin Enrique Ortega Aguilar', 'Agente de Seguridad'),
    (148, 9505, 'f5f242de-b63c-3fa7-a55a-e5c72716ce6a'::uuid, 'Javier Antonio Miranda Garces', 'Agente de Seguridad'),
    (149, 9503, 'e61be526-0298-3b2c-b643-a8f3f76eaee6'::uuid, 'Jonathan Vicente Medina Valencia', 'Agente de Seguridad'),
    (150, 9502, 'adb52674-3844-360e-b9f2-3c538fe9e018'::uuid, 'Cinthia Valeria Veliz Viera', 'Agente de Seguridad'),
    (151, 9501, '3b6f9484-93e2-36ea-ba0e-92fe33a06168'::uuid, 'David Daniel Chichande Carranza', 'Agente de Seguridad'),
    (152, 9500, '37e701bb-2f95-30b2-a9e7-5d6e74714c53'::uuid, 'Christopher Daniel Rodriguez Carrasco', 'Agente de Seguridad'),
    (153, 9498, '4ffaea89-39c7-3802-8b53-249002dc2f5b'::uuid, 'Reinelio  Conte Garrido', 'Agente de Seguridad'),
    (154, 9495, 'd19b4d4d-e5f5-3b72-a13b-1a41d80eea5f'::uuid, 'Orlando Mauricio Ramos Jimenez', 'Agente de Seguridad'),
    (155, 9493, '55a3eb9d-c6ed-3b01-91e7-dea70f3246b7'::uuid, 'Jonathan David Vasquez Pineda', 'Agente de Seguridad'),
    (156, 9491, 'ecf50036-73fa-3422-a9b3-6e9cce1b7239'::uuid, 'Christian Esteban Rojas Vargas', 'Agente de Seguridad'),
    (157, 9490, 'd1c4d38c-3787-3408-8d53-0b14e46f3844'::uuid, 'Joel Jordano Velez Macias', 'Agente de Seguridad'),
    (158, 9489, '8455076b-db34-3a0e-bd90-686196684d21'::uuid, 'Oswaldo Florentino Vargas Ayala', 'Agente de Seguridad'),
    (159, 9488, '594d1cc8-de0c-3bca-a8c1-13552b8ce3e5'::uuid, 'Juan Abigdail Flores Gonzalez', 'Agente de Seguridad'),
    (160, 9485, '2ca86f83-4ce8-3c4b-82cd-725634344792'::uuid, 'Geovanny Jose Freire Romero', 'Agente de Seguridad'),
    (161, 9484, '788e00cc-3979-3ff0-bb6c-a89fb2976c79'::uuid, 'Luis Felipe Moreira Anchundia', 'Agente de Seguridad'),
    (162, 9483, 'd41b8fb2-7450-3228-81b0-62d8fa063718'::uuid, 'Anshelo Stalin Soliz Llanos', 'Agente de Seguridad'),
    (163, 9481, '24867667-8c26-3eb7-a2da-f9dd3dc4ca6c'::uuid, 'Segundo Hernán Vásquez Marín', 'Agente de Seguridad'),
    (164, 9480, '6f030c98-1dd0-3913-b6c4-2de91cfd322f'::uuid, 'Jesús Michael Alvarado Jimenez', 'Agente de Seguridad'),
    (165, 9479, '583928f4-fcad-396f-8c64-d89262c69f99'::uuid, 'Ariel Fernando Zamora Sandoval', 'Agente de Seguridad'),
    (166, 9478, '8420c5ad-1c9e-39ed-90ee-2281540d5bfb'::uuid, 'Henrry Walter Guale León', 'Agente de Seguridad'),
    (167, 9477, 'e5e332c4-c591-3bdc-b354-db137dd96ef4'::uuid, 'Axel Israel Delgado Zavala', 'Agente de Seguridad'),
    (168, 9476, '1069c7c1-7409-328c-b0e2-1081c7f147c0'::uuid, 'Jordan Miguel Mullo Quezada', 'Agente de Seguridad'),
    (169, 7780, 'dea5e010-f69c-3648-8e25-075553d891a9'::uuid, 'Maximiliano Jordan Moran Moran', 'Agente de Seguridad'),
    (170, 9474, 'dbcfede2-709b-3b36-895a-d7888c212994'::uuid, 'Isaias Enmanuel Ponce Enriquez', 'Agente de Seguridad'),
    (171, 9473, 'f754fb72-6a70-30bc-ab02-6395c19788dc'::uuid, 'Maykel Bryan Izquierdo Nazareno', 'Agente de Seguridad'),
    (172, 9471, '23bd53a7-8340-3c4b-a866-215bb1cb37af'::uuid, 'Ronald Alexander Farias Chichande', 'Agente de Seguridad'),
    (173, 9466, '2bbe0635-09b9-386d-993d-2aa08d8053ad'::uuid, 'Jean Carlos Moncayo Monserratt', 'Agente de Seguridad'),
    (174, 9465, '26941a62-d9d3-32a9-a405-4a48cbbc4d29'::uuid, 'Josue Damián Maya Zavala', 'Agente de Seguridad'),
    (175, 9464, 'fb85b9e1-0c72-309d-a7dc-5b8c0db2d568'::uuid, 'Rubén Enrique Espinoza Quiroz', 'Agente de Seguridad'),
    (176, 9460, '1de4326d-a761-3a89-84a7-973096588ea7'::uuid, 'Emerson Abel Taday Iñiguez', 'Agente de Seguridad'),
    (177, 9459, '4a9ba6a4-9d4d-32f2-8cde-3852d1876111'::uuid, 'Luis Javier Lombeida Molina', 'Agente de Seguridad'),
    (178, 9457, '862fc773-943f-3c4d-a21b-0f04d876700a'::uuid, 'Eddie Rolando Salazar Mejia', 'Agente de Seguridad'),
    (179, 9454, '94eb83b2-3811-3586-9e32-280cf02294ca'::uuid, 'Manuel Benigno Cevallos Cedeño', 'Agente de Seguridad'),
    (180, 9450, 'ee5e0a23-e0c5-3a8c-9085-94641aa8854a'::uuid, 'Jorge Luis Abrego vasquez', 'Agente de Seguridad'),
    (181, 9448, 'c851c2b0-6e2e-3dc5-97c3-3b88500d31c8'::uuid, 'Wilmer Antonio Martinez Mendoza', 'Agente de Seguridad'),
    (182, 9447, '36bc4641-9691-3d16-a153-e761e21045c0'::uuid, 'Bladimir Abel Rodríguez Carmona', 'Agente de Seguridad'),
    (183, 9446, 'feed8e59-c0e2-390c-a55c-182973f0824b'::uuid, 'Angel Jahir Cáceres Castro', 'Agente de Seguridad'),
    (184, 9445, '32590024-2f41-3d0a-be03-fc4542c87c54'::uuid, 'Constantino  Gonzalez oliveros', 'Agente de Seguridad'),
    (185, 9444, '5d0d340b-7b86-3fef-903a-2dfb9421bbdc'::uuid, 'Jose Alexis Espinosa ruiz', 'Agente de Seguridad'),
    (186, 9443, '68f12d95-ed28-3def-baa2-9c8fce90e78e'::uuid, 'Leonardo  Caballero', 'Agente de Seguridad'),
    (187, 9441, '26b6a3c1-d55d-3e35-8e28-72a3c83867f1'::uuid, 'Manuel Enrique Quiroz Gonzalez', 'Agente de Seguridad'),
    (188, 9440, '7c27a471-f3c8-3f8a-8792-cadfe21d4f3d'::uuid, 'Luis Alberto Gonzalez Contreras', 'Agente de Seguridad'),
    (189, 9438, '15d1cbdb-9e9c-3b8c-9402-a8f355bd27ca'::uuid, 'Pedro Jesus Gonzalez Valero', 'Agente de Seguridad'),
    (190, 9437, 'c9526095-9301-393e-a3eb-90bc31fff8aa'::uuid, 'Victor Aaron Alvarado Mendoza', 'Agente de Seguridad'),
    (191, 9436, 'e4b4fefb-832e-3a62-8aaf-eeecda575c98'::uuid, 'Javier Luis Chamba Reyna', 'Agente de Seguridad'),
    (192, 9434, '88145b57-0a03-3b3c-aec2-3c7c0c13aa2c'::uuid, 'Stalin Raúl Grande Arequipa', 'Agente de Seguridad'),
    (193, 9432, 'e7e3a20e-6a63-3d1d-94ff-723cf0b131a5'::uuid, 'Victor Antonio Galvez Rogel', 'Agente de Seguridad'),
    (194, 9430, '7f412067-f70c-33e1-9de2-843915c539ab'::uuid, 'Ronald Eduardo Garces Monserrate', 'Agente de Seguridad'),
    (195, 9428, 'c9643c95-e1a4-3bb3-b171-ca19423c461e'::uuid, 'Lisander Jeron Prias Oña', 'Agente de Seguridad'),
    (196, 9426, 'd29d8075-696e-3e91-be3b-e0259422e63e'::uuid, 'Luis Kleber Vega Jacome', 'Agente de Seguridad'),
    (197, 9424, '80f908ab-693f-3b86-88ba-d1e51eb271b5'::uuid, 'Erick Joel Quizhpi Buñay', 'Agente de Seguridad'),
    (198, 9423, '6111ea0d-4e88-3fbb-90aa-728f804bdd66'::uuid, 'Steven Bryan Guzman Franco', 'Agente de Seguridad'),
    (199, 9422, '2e07801f-8824-3e0c-8532-126b1ca3a274'::uuid, 'Leonel Ariel Triana Magallanes', 'Agente de Seguridad'),
    (200, 9421, '6e2a921d-e4ed-36f0-8555-3c8744bec6b8'::uuid, 'Ivan Arturo Jordan Jama', 'Agente de Seguridad');

CREATE TEMP TABLE dme_companias_carga ON COMMIT DROP AS
SELECT
    row_number() OVER (ORDER BY code) AS company_order,
    id AS company_id,
    code,
    name
FROM company
WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
  AND status = 'ACTIVE'
  AND code IN ('COM-001', 'COM-002', 'COM-003', 'COM-004', 'COM-006', 'KAI-001');

DO $dme$
DECLARE
    employee_count bigint;
    company_count bigint;
    previous_data_count bigint;
BEGIN
    SELECT count(*) INTO employee_count FROM dme_empleados_carga;
    SELECT count(*) INTO company_count FROM dme_companias_carga;

    SELECT
        (SELECT count(*) FROM employee_operational_snapshot WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid)
      + (SELECT count(*) FROM company_membership WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid)
      + (SELECT count(*) FROM employee_skill_snapshot WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid)
      + (SELECT count(*) FROM employee_unavailability_snapshot WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid)
      + (SELECT count(*) FROM employee_company_transfer WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid)
      INTO previous_data_count;

    IF employee_count <> 200 THEN
        RAISE EXCEPTION 'Carga cancelada: se esperaban 200 empleados y se obtuvieron %.', employee_count;
    END IF;

    IF company_count <> 6 THEN
        RAISE EXCEPTION 'Carga cancelada: deben existir activas exactamente las seis compañías esperadas; se encontraron %.', company_count;
    END IF;

    IF previous_data_count <> 0 THEN
        RAISE EXCEPTION 'Carga cancelada: aún existen % registros del catálogo anterior. Ejecute primero el script de eliminación.', previous_data_count;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM dme_empleados_carga
         WHERE persona_id <= 0
            OR btrim(full_name) = ''
            OR length(full_name) > 180
            OR btrim(role_code) = ''
            OR length(role_code) > 80
    ) THEN
        RAISE EXCEPTION 'Carga cancelada: la fuente contiene datos obligatorios inválidos o fuera del tamaño permitido.';
    END IF;
END
$dme$;

CREATE TEMP TABLE dme_empleados_asignados ON COMMIT DROP AS
SELECT
    e.row_number,
    e.persona_id,
    e.employee_id,
    e.full_name,
    e.role_code,
    c.company_id,
    c.code AS company_code,
    c.name AS company_name
FROM dme_empleados_carga e
JOIN dme_companias_carga c
  ON c.company_order = 1 + mod(e.row_number - 1, 6);

INSERT INTO employee_operational_snapshot (
    id,
    instance_country_id,
    employee_id,
    persona_id,
    company_id,
    full_name,
    role_code,
    employment_status,
    id_score,
    preferred_shift,
    required_change,
    photo_key,
    updated_from_source_at,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    '11111111-1111-1111-1111-111111111111'::uuid,
    employee_id,
    persona_id,
    company_id,
    full_name,
    role_code,
    'ACTIVE',
    NULL,
    NULL,
    false,
    '/avatars/' ||
    CASE
        WHEN persona_id IN (8034, 9610, 9582, 9573, 9557, 9548, 9508, 9502) THEN
            (ARRAY[
                '82000000-0000-0000-0000-000000000002.png',
                '82000000-0000-0000-0000-000000000006.png',
                '82000000-0000-0000-0000-000000000008.png',
                '82000000-0000-0000-0000-000000000010.png',
                '82000000-0000-0000-0000-000000000012.png'
            ])[1 + mod(row_number - 1, 5)]
        ELSE
            (ARRAY[
                '82000000-0000-0000-0000-000000000001.png',
                '82000000-0000-0000-0000-000000000003.png',
                '82000000-0000-0000-0000-000000000004.png',
                '82000000-0000-0000-0000-000000000005.png',
                '82000000-0000-0000-0000-000000000007.png',
                '82000000-0000-0000-0000-000000000009.png',
                '82000000-0000-0000-0000-000000000011.png'
            ])[1 + mod(row_number - 1, 7)]
    END,
    now(),
    now(),
    now()
FROM dme_empleados_asignados
ORDER BY row_number;

INSERT INTO company_membership (
    id,
    instance_country_id,
    company_id,
    employee_id,
    membership_type,
    role_code,
    starts_at,
    ends_at,
    required_change,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    '11111111-1111-1111-1111-111111111111'::uuid,
    company_id,
    employee_id,
    'PRIMARY',
    role_code,
    now(),
    NULL,
    false,
    now(),
    now()
FROM dme_empleados_asignados
ORDER BY row_number;

DO $dme$
DECLARE
    snapshot_count bigint;
    membership_count bigint;
    persona_count bigint;
    avatar_count bigint;
BEGIN
    SELECT count(*), count(persona_id)
      INTO snapshot_count, persona_count
      FROM employee_operational_snapshot
     WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

    SELECT count(*) FILTER (WHERE photo_key IS NOT NULL AND btrim(photo_key) <> '')
      INTO avatar_count
      FROM employee_operational_snapshot
     WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

    SELECT count(*)
      INTO membership_count
      FROM company_membership
     WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
       AND membership_type = 'PRIMARY'
       AND ends_at IS NULL;

    IF snapshot_count <> 200 OR persona_count <> 200 OR avatar_count <> 200 OR membership_count <> 200 THEN
        RAISE EXCEPTION
            'Validación final fallida: snapshots=%, persona_id=%, avatares=%, membresías activas=%. Se revierte la transacción.',
            snapshot_count, persona_count, avatar_count, membership_count;
    END IF;
END
$dme$;

SELECT
    c.code AS codigo_compania,
    c.name AS compania,
    count(*) AS empleados
FROM employee_operational_snapshot eos
JOIN company c ON c.id = eos.company_id
WHERE eos.instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
GROUP BY c.code, c.name
ORDER BY c.code;

SELECT photo_key AS avatar, count(*) AS empleados
FROM employee_operational_snapshot
WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
GROUP BY photo_key
ORDER BY photo_key;

SELECT role_code AS cargo, count(*) AS empleados
FROM employee_operational_snapshot
WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
GROUP BY role_code
ORDER BY role_code;

COMMIT;
