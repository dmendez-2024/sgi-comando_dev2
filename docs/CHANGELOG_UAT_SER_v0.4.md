# SGI: Comando — CHANGELOG UAT SER v0.4

## Configuración > Puestos
- Nueva página hija accesible desde la landing de Configuración del Punto.
- Lista de Puestos recibidos desde SIC: COM.
- Datos contractuales de solo lectura: Código, Nombre, TIER, Formato, Rotación, Turnos y Horas requeridas.
- Tipo de Puesto: CAA, PAT, VIG y MIX.
- Descripción operacional editable.
- Selección de ubicación directamente sobre el plano ATS.
- 8 habilidades requeridas con sliders gamificados.
- Economía institucional: mínimo 1 por habilidad, máximo una habilidad en 5, máximo dos habilidades en 4, suma máxima 22.
- Validación de estas reglas tanto en API como en base de datos.
- Campo de justificación cuando el usuario se aparta de la plantilla sugerida.
- Permisos Requeridos creado visualmente como Próximamente, sin funcionalidad.
- Persistencia de borrador / configuración mediante `post_operational_config`.

## Congelados
- TER v1.0, COM v1.0 y ASI v0.6.5 permanecen congelados.
- La página ATS se conserva según SER v0.3.2; esta corrida no busca pulir ATS.
