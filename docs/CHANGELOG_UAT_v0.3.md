# SGI: Comando — UAT v0.3

## Verticales
- SGI-00T Territorio v0.1 — nueva vertical.
- SGI-06 Asignaciones v0.3 — vertical activa.

## Cambios funcionales
### Territorio
- Nuevo menú `Operaciones → Territorio`.
- Configuración de Zonas y Regiones.
- Asignación de Compañía a Región.
- Ecuador UAT: ZN/ZS y R-N1..R-S3.
- Scope territorial server-side para País/Zona/Región/Compañía en Compañías, Servicios/Puntos/Puestos, Territorio y Asignaciones.
- Usuarios UAT agregados: Presidente, Director Operaciones LATAM y Jefe Regional.

### Asignaciones
- Click en persona → evaluación de cada ShiftOccurrence con backend real.
- Verde = asignable; ámbar = asignable con warnings; rojo = bloqueo.
- 12 retratos ficticios locales.
- Tarjetas rediseñadas: nombre a dos líneas, foto, ID, IC y auditoría más legibles.
- Corrección de espaciado del rango horario.
- Caso SIC: COM de un mismo Puesto con 3 Turnos de 8 h.
- Se conserva Drag & Drop, publicación, cobertura publicada/actual y snapshot inmutable de v0.2.

## No incluido en v0.3
- Copia de ciclo/semana a futuro: registrado como `SGI-06-BACKLOG-001`.
