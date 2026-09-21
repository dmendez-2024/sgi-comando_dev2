# SGI: Comando — Project Context

**Método de trabajo vigente:** desarrollo y congelamiento **por vertical**. No se realizan UAT generales que modifiquen múltiples verticales simultáneamente.

## Stack base

- Frontend: React 19.2 + TypeScript 6.
- Backend: Java 25 LTS + Quarkus.
- Base de datos: PostgreSQL 17.
- Objetos/evidencia: MinIO.
- Deploy local/UAT: Docker Compose.
- Arquitectura: modular monolith con fronteras de dominio y adapters externos.
- Aislamiento: `instance_country_id`.

## Verticales congeladas

### TER — Territorio v1.0 FROZEN

SoR operacional de Zonas/Regiones; CORE continúa siendo SoR de geografía oficial, catálogo y polígonos.

### COM — Compañías v1.0 FROZEN

Zona única, multi-región, Logo, Reseña Histórica, edición versionada, inactivación condicionada a Servicios activos y reactivación.

## Filosofía de freeze

Una vertical congelada no se modifica silenciosamente. Un cambio posterior exige nueva versión, Decision Log, SITC delta y regresión.

## Handoff final

Cada paquete vertical conserva:

- definición funcional
- Decision Log
- contexto
- modelo de datos
- contratos e integraciones
- criterios de aceptación
- UAT/regresión
- changelog
- freeze note
- instrucciones a Sistemas
- `.sitcpack`

El archivo `sitc/SGI_Comando_CURRENT.sitcpack` representa la arquitectura acumulada conocida y debe viajar con cada entrega posterior.

## SER v0.5 — Configuración ATS
Servicios/Configuración ya consume archivos `.ats` reales. SGI preserva el paquete fuente, versiona importaciones, materializa el plano y lo reutiliza en la selección geométrica de ubicaciones de Puestos. SIC: COM continúa siendo SoR de los atributos contractuales de Puestos.
