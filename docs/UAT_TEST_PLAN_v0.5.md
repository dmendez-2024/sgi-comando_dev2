# SGI: Comando — UAT Test Plan v0.5

## A. Territorio v0.3
- **TERR-05-01**: mapa superior muestra mapa operacional de Ecuador con Zonas/Regiones y no la lámina de grados.
- **TERR-05-02**: Provincias/Estados se identifican como catálogo CORE LOCAL en UAT.
- **TERR-05-03**: Guardar Zona muestra mensaje visible de éxito y desaparece automáticamente.
- **TERR-05-04**: Guardar Región muestra mensaje visible de éxito y desaparece automáticamente.
- **TERR-05-05**: pantalla es legible a zoom 100%.
- **TERR-05-06**: Director Nacional ve país; Director Zonal solo Zona; Jefe Regional solo Región; Coordinador solo contexto de Compañía.

## B. Asignaciones v0.5
> Para pruebas de edición seleccionar **Coordinador Compañía** o **Asistente de Operaciones**. Presidente/Directores son intencionalmente solo lectura.

- **ASG-05-01**: arrastrar una persona desde Personal disponible hacia un Turno asignable crea la asignación.
- **ASG-05-02**: seleccionar una persona resalta Verde/Ámbar/Rojo según evaluación backend.
- **ASG-05-03**: click en persona del pool abre ficha operacional y deja activa la evaluación de elegibilidad.
- **ASG-05-04**: Vacaciones usa icono aprobado de vacaciones.
- **ASG-05-05**: Permiso Médico usa icono aprobado de botiquín/cruz.
- **ASG-05-06**: tarjeta asignada no superpone warning sobre ID/IC a resoluciones desktop/laptop.
- **ASG-05-07**: Turno 22:00–06:00 se visualiza como 22:00–24:00 en Día 1 y 00:00–06:00 en Día 2.
- **ASG-05-08**: Drag & Drop sobre cualquiera de los dos segmentos del mismo Turno llena ambos segmentos.
- **ASG-05-09**: eliminar desde cualquiera de los segmentos elimina la asignación lógica completa sin modal de confirmación.
- **ASG-05-10**: selección múltiple sobre cualquiera de los segmentos selecciona el Turno lógico y refleja selección en ambos segmentos.
- **ASG-05-11**: copiar/pegar conserva operación por Turno lógico y revalida reglas en destino.
- **ASG-05-12**: a viewport estrecho la matriz hace scroll horizontal interno y las tarjetas conservan legibilidad.

## Resultado
Registrar cada criterio como `PASS`, `FAIL` o `AJUSTE` en el siguiente checkpoint UAT.
