# SGI: Comando — CSL v0.1.1 UAT

## Vertical
`Operaciones > Consola`

## Baseline
`CSL v0.1` sobre `SGI_Comando_P0P1_RC_2026-09-27.zip`.

## Cambio autorizado
La zona completa de búsqueda/filtros pasa a un accordion/collapsible.

### Estado inicial
- Cerrado por defecto al abrir la página.
- Solo se muestra la fila `Filtros de búsqueda` con icono de filtro y chevron.
- La tabla de `Casos operativos` permanece visible.

### Estado expandido
Al pulsar `Filtros de búsqueda` se despliegan, sin cambiar su lógica existente:
- búsqueda general;
- Ciudad;
- Compañía;
- Cliente;
- Punto;
- Puesto;
- Estado;
- Responsable;
- Fecha inicio / Fecha fin;
- reglas/validaciones;
- Buscar / Limpiar filtros / Exportar.

## Persistencia durante la interacción
Colapsar o expandir el panel no limpia filtros ni resultados. Solo controla visibilidad.

## Accesibilidad
El control usa `button`, `aria-expanded` y `aria-controls`; el chevron refleja el estado abierto/cerrado.

## Fuera de alcance
- Sin cambios de backend.
- Sin migraciones.
- Sin cambios de interconexiones/SITC.
- Sin EVC / Eventos de Cumplimiento.
- Sin cambios a otras verticales.
