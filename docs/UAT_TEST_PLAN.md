# SGI: Comando — UAT v0.2.2.2 — Plan de pruebas

## Vertical activa
**SGI-06 Asignaciones v0.2**

## Secuencia recomendada
1. Abrir Asignaciones para Compañía Galvarino y semana vigente.
2. Confirmar que el pool muestra Agentes, Supervisores y Escoltas desde RRHH LOCAL.
3. Confirmar que personal con vacaciones/permiso médico sigue visible y marcado.
4. Intentar asignar durante indisponibilidad: debe bloquear.
5. Asignar personal válido por Drag & Drop: debe persistir.
6. Verificar warnings de IC/rol/ID-TIER/Cambio Requerido: deben permitir asignar.
7. Publicar con al menos una vacante: debe permitirse con advertencia.
8. Verificar Cobertura Publicada en el resumen.
9. Cubrir una vacante del Plan publicado: debe mostrarse como **Asignación posterior a publicación**.
10. Cambiar el Agente de un Turno ya publicado: debe registrarse como **Reasignación** con motivo/autor/fecha.
11. Confirmar que Cobertura Publicada no cambia y Cobertura Actual sí.
12. Reiniciar navegador/contenedores y confirmar persistencia PostgreSQL.
13. Consultar historial de la asignación y confirmar trazabilidad.
14. Validar cierre automático con una semana histórica/publicada de prueba cuando se prepare el fixture correspondiente.

## Criterios
| ID | Criterio | Estado previo a UAT local |
|---|---|---|
| AC-ASG-01 | Personal por Compañía desde SIC: RRHH LOCAL | PENDIENTE UAT |
| AC-ASG-02 | Vacaciones/permiso médico por intervalo bloquean | PENDIENTE UAT |
| AC-ASG-03 | Warnings no bloqueantes | PENDIENTE UAT |
| AC-ASG-04 | Turnos exactos SIC: COM | PENDIENTE UAT |
| AC-ASG-05 | Drag & Drop persiste | PENDIENTE UAT |
| AC-ASG-06 | Publicación con vacantes | PENDIENTE UAT |
| AC-ASG-07 | Snapshot publicado inmutable | PENDIENTE UAT |
| AC-ASG-08 | Asignación posterior a publicación diferenciada | PENDIENTE UAT |
| AC-ASG-09 | Reasignación auditable | PENDIENTE UAT |
| AC-ASG-10 | Cobertura Publicada vs Actual | PENDIENTE UAT |
| AC-ASG-11 | Cierre automático semanal | PENDIENTE UAT |
| AC-ASG-12 | Copia múltiple parcial | PENDIENTE IMPLEMENTACIÓN UI |
| AC-ASG-13 | Semáforo preventivo de drag | PENDIENTE IMPLEMENTACIÓN UI |
