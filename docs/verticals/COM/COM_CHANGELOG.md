# COM — Changelog

## v1.0 FROZEN — 2026-09-08

- Congelamiento formal de COM.
- Sin cambios funcionales sobre el código aceptado en COM v0.1.
- Consolidación de contexto, decisiones, modelo de datos, API, integraciones y handoff a Sistemas.
- Se congela almacenamiento productivo de Logo en MinIO.
- Se congela contrato futuro con SER: Región operacional explícita por Servicio.
- Se agrega `COM_v1.0_FROZEN_delta.sitcpack` y pack acumulativo `SGI_Comando_CURRENT.sitcpack`.

## v0.1 — Candidato UAT

- Logo y Reseña Histórica (máx. 750 caracteres).
- Edición de Compañías.
- Zona única + múltiples Regiones.
- Bloqueo de retiro de Región con Servicios activos.
- Bloqueo de inactivación con Servicios activos.
- Reactivación.
- Historial y versionamiento.

## Extensiones posteriores descritas en el código — estado por formalizar

- **V28 / responsable operacional:** `cambios/CHANGELOG_DME_COM_RESPONSABLE_2026-09-24.md` describe columna nullable, API y pruebas; el comentario de V28 denomina el cambio COM v1.1.4. CR-ID/aprobación y release formal no constan en esta matriz documental.
- **V33 / catálogo comercial entrante:** `cambios/CHANGELOG_JTO_IMPLEMENTACION_CATALOGO_SIC_COM.md` describe Cliente, referencia desde Servicio, estado comercial de Puesto y recibo idempotente. El changelog citado dice V27, pero el código actual lo contiene como V33.
- Estos registros documentan extensiones detectadas; no alteran ni sustituyen retroactivamente la baseline COM v1.1.3 FROZEN. UAT y estado de aprobación deben anotarse en una RC autorizada.
