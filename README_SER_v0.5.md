# SGI: Comando — SER v0.5

Vertical activa: **Servicios / Configuración**.

## Flujo funcional
`Servicios → Configuración del Punto → ATS → Importar .ats`

El archivo `.ats` es un paquete ZIP publicado por ATS. SGI no altera el paquete: conserva el binario original, lee sus metadatos y materializa el plano principal para usarlo en Configuración.

Después de importar el archivo:
1. ATS muestra el plano real y metadatos de publicación.
2. Puestos consume ese mismo plano.
3. La ubicación de cada Puesto se elige directamente sobre la imagen y se guarda como coordenada normalizada vinculada a la revisión ATS vigente.

## Ownership
- ATS: diseño publicado del Punto y plano fuente.
- SIC: COM: Servicio / Punto / Puesto / TIER / Formato / Rotación / Turnos.
- SGI: Comando: configuración operacional y vínculos sobre el plano publicado.

## UAT
El primer archivo `.ats` debe importarse desde la página ATS antes de configurar ubicaciones de Puestos sobre el plano real.
