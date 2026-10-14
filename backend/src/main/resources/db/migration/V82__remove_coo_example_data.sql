-- Quita los datos de ejemplo de Coordinación que insertaba V25 (Puestos MON-001, SUP-001, SUP-002 y la Ruta Norte con su Punto).
-- V25 ya está aplicada en las bases existentes y no se edita: Flyway valida su checksum y el backend no arrancaría.
-- Solo se borra lo que nada usa (turnos, relevos, rutas derivadas); si un ambiente ya los usa, se conservan.

-- Ruta Norte (sus Puntos se borran en cascada).
DELETE FROM supervision_route r
WHERE r.id = 'c2000000-0000-0000-0000-000000000001'::uuid
  AND NOT EXISTS (SELECT 1 FROM shift_occurrence s WHERE s.supervision_route_id = r.id)
  AND NOT EXISTS (SELECT 1 FROM supervision_route d WHERE d.based_on_route_id = r.id);

-- Puestos de Coordinación de ejemplo, si ya no tienen Rutas, turnos ni relevos.
DELETE FROM coordination_post p
WHERE p.id IN ('c1000000-0000-0000-0000-000000000001'::uuid,
               'c1000000-0000-0000-0000-000000000002'::uuid,
               'c1000000-0000-0000-0000-000000000003'::uuid)
  AND NOT EXISTS (SELECT 1 FROM supervision_route r WHERE r.coordination_post_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM shift_occurrence s WHERE s.coordination_post_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM relief_event e WHERE e.coordination_post_id = p.id);
