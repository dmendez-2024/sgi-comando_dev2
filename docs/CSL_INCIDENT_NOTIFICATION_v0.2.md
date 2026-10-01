# CSL v0.2 — Notificación de Incidentes

## 1. Objetivo

Permitir que un Operador de Consola registre y gestione una Notificación de Incidente desde `Operaciones > Consola`, sin abandonar la bandeja operativa unificada.

## 2. Formulario general

Orden funcional:
1. Título.
2. Categoría: Servicio / Seguridad / Administrativo.
3. Subcategoría.
4. Criticidad: Informativo / Menor / Moderado / Mayor / Crítico.
5. Cliente / Punto obligatorios; Puesto opcional.
6. Uno o más colaboradores relacionados.
7. Descripción + hasta 5 imágenes.
8. Resolución + hasta 5 imágenes.
9. Sanción: Sí/No; si Sí, descripción de la sanción.
10. Guardar Borrador o Finalizar.

Los casos creados quedan visibles en `Casos operativos`. Tanto Borrador como Finalizado pueden reabrirse para edición.

## 3. Taxonomía

Desde v0.2.1 se usa la referencia exacta `Incidentes(1).xlsx` recibida el 2026-09-27 con tres niveles: **Categoría → Subcategoría → Incidente**:

- Servicio: Asistencia y Puntualidad; Presentación Personal; Disciplina; Cumplimiento Operativo; Competencia Profesional; Atención al Cliente.
- Seguridad: Delitos; Accesos No Autorizados; Emergencias Médicas; Incendios y Riesgos; Seguridad Física; Desastres Naturales; Riesgos Operacionales; Infraestructura.
- Administrativo: Talento Humano; Logística MARE; Logística Vehículos; Tecnología; Documentación; Comercial.

`Inasistencia programada` e `Inasistencia efectiva` son tipos de incidente bajo `Servicio → Asistencia y Puntualidad`. Al seleccionar cualquiera de ellos se activa el flujo especial de cobertura/reasignación.

## 4. Colaboradores

La lista debe construirse con personas que hayan trabajado en el Punto seleccionado entre `ahora` y `ahora - 14 días`, sin duplicados. El backend productivo deberá resolverlo desde Asignaciones/ejecución histórica de SGI: Comando. La UAT usa datos DEMO para validar UX y reglas.

## 5. Inasistencia programada

- El Operador elige uno de los siguientes dos turnos.
- Un Agente está libre cuando no está de turno en el turno a cubrir ni en el turno inmediatamente previo.
- Se presenta ranking de candidatos:
  1. mismo Puesto;
  2. mismo Punto;
  3. misma Compañía por cercanía geográfica del último Punto donde prestó servicio.

## 6. Inasistencia efectiva

- El turno objetivo es el turno actualmente en curso.
- Un Agente está libre cuando no está de turno ahora ni en el turno previo.
- Se aplica la misma prelación de candidatos.

## 7. Criticidad

Escala visual independiente de la prioridad genérica de otros dominios:
- Informativo
- Menor
- Moderado
- Mayor
- Crítico

Para mostrar el incidente dentro de la tabla general de Consola, la UAT proyecta criticidad a la columna Prioridad sin alterar los catálogos de otros módulos.

## 8. Persistencia y routing

CSL v0.2 es UAT de interfaz/reglas. No crea todavía persistencia productiva para este formulario y no activa routing STC. Los contratos SGI_COM ↔ STC existentes permanecen sin cambios.

## 9. Fuera de alcance

- EVC / Eventos de Cumplimiento.
- Integración real con sensores.
- Persistencia binaria productiva de imágenes.
- Modificaciones a SIC: RRHH por sanciones.

## Actualización v0.2.1 — taxonomía Excel 2026-09-27

La fuente `Incidentes(1).xlsx` confirma la jerarquía:
`Categoría → Subcategoría → Incidente`.

Conteo: 3 categorías, 20 subcategorías, 90 incidentes.

`Inasistencia programada` e `Inasistencia efectiva` son incidentes dentro de:
`Incidentes de Servicio → Asistencia y Puntualidad`.

El flujo de cobertura/reasignación se activa al seleccionar esos incidentes.
