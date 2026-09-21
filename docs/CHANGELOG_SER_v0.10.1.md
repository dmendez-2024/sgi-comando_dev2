# SGI: Comando — SER v0.10.1 UAT

Fecha: 2026-09-19
Baseline: SER v0.9.1 + COM v1.1.3 FROZEN + ASI v0.7.4

## Cambios incluidos

### 1. Estados de Protocolos
Bitácora, Patrullas y Consignas usan exactamente:
- Borrador
- Inactivo
- Activo

Flujo:
- Borrador -> Inactivo: Publicar
- Inactivo -> Activo: Activar
- Activo -> Inactivo: Inactivar

`Activo` reemplaza a `Vigente` en la UI de Protocolos.

### 2. Bitácora — máximo 10 Acreditaciones
- Máximo 10 Acreditaciones por Protocolo.
- La UI muestra `Acreditaciones (X/10)`.
- La creación número 11 se bloquea tanto en frontend como en backend.
- Mensaje funcional: `Este Protocolo ya alcanzó el máximo de 10 acreditaciones. Para continuar, crea un nuevo Protocolo.`

### 3. Patrullas — UI homologado con Bitácora
Nueva composición:
- izquierda: Puestos del Punto
- centro: Protocolos del Puesto
- derecha: detalle del Protocolo
  - Patrullas del protocolo en tarjetas horizontales
  - Configuración de la Patrulla seleccionada
  - tabs Definición / Programación / Hitos / Reglas por hito / Evidencias / Versionado

## Puertos UAT
- Frontend: http://localhost:5173
- Backend: http://localhost:8080

`uat-open.ps1` abre siempre el Frontend en 5173.
