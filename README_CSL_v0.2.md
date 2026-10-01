# SGI: Comando — CSL v0.2 UAT

Fecha: 2026-09-27  
Baseline: `CSL v0.1.1 UAT` sobre `SGI_Comando_P0P1_RC_2026-09-27.zip`  
Vertical: `Operaciones > Consola`  
Estado: `UAT_CANDIDATE`

## Cambio principal

Se incorpora el flujo **Notificar Incidente** solicitado en `Pagina Consola.docx`, preservando el accordion de filtros de CSL v0.1.1.

La página incorpora:

- botón `Notificar Incidente`;
- panel derecho `Notificación de Incidente`;
- categoría Servicio / Seguridad / Administrativo con iconografía de la especificación;
- subcategorías de `Incidentes.xlsx` según `docs/spec/REFERENCE_INCIDENT_TAXONOMY.md`;
- criticidad Informativo / Menor / Moderado / Mayor / Crítico;
- Cliente + Punto obligatorios y Puesto opcional;
- selección múltiple de colaboradores que trabajaron en el Punto en las últimas dos semanas, sin duplicados;
- Descripción y Resolución con hasta 5 imágenes por sección;
- Sanción Sí/No + descripción;
- `Guardar borrador` y `Finalizar`;
- Borradores/Finalizados aparecen en Casos operativos y pueden reabrirse para edición;
- flujo especial para `Inasistencia Programada` e `Inasistencia Efectiva` con selección de turno, lista de Agentes disponibles y prelación operacional.

## Regla de reasignación UAT

Prelación:
1. libres del mismo Puesto;
2. libres del mismo Punto;
3. libres de la misma Compañía, ordenados por cercanía geográfica entre el último Punto trabajado y el Punto a cubrir.

Disponibilidad:
- Programada: libre en el turno a cubrir y en el turno previo.
- Efectiva: libre en el turno actual y en el turno previo.

## Alcance técnico de esta UAT

Esta entrega valida UX, reglas de formulario, estados y ranking. El catálogo de colaboradores/historial/turnos usado por el panel es DEMO/local para UAT; no agrega tablas ni endpoints productivos en esta versión. La persistencia productiva, almacenamiento binario de imágenes y conciliación con STC deben implementarse sobre contratos/backend en una vertical posterior.

## Arquitectura

- Backend: sin cambios.
- Base de datos/Flyway: sin cambios.
- SITC/interconexiones: sin cambios.
- `SGI_Comando_CURRENT.sitcpack`: preservado sin modificación.
- EVC/Eventos de Cumplimiento: fuera de alcance.

## UAT sugerido

1. Abrir `Operaciones > Consola`.
2. Verificar que `Filtros de búsqueda` inicia cerrado.
3. Pulsar `Notificar Incidente`.
4. Crear un incidente normal, adjuntar hasta 5 imágenes por sección, guardar como Borrador y reabrirlo desde Casos operativos.
5. Finalizarlo y reabrirlo nuevamente para editar.
6. Crear `Servicio > Inasistencia Programada`, elegir uno de los dos turnos y validar ranking de Agentes.
7. Crear `Servicio > Inasistencia Efectiva` y validar uso automático del turno en curso.

Validación estática: `docs/VALIDATION_CSL_v0.2_2026-09-27.md`.
