# SGI: Comando — CHANGELOG UAT SER v0.5

## Configuración > ATS — importación real `.ats`
- `Importar archivo` abre selector local restringido a `.ats`.
- SGI recibe el archivo binario y lo valida como paquete ZIP ATS.
- Se exige `manifest.json` y `model/pto.json`.
- Se valida `atsSchemaVersion`, `packageType` y la existencia del plano declarado por ATS.
- El plano se materializa desde `plan.internalPath` y se valida su SHA-256 cuando el paquete lo informa.
- Formatos de imagen admitidos para el plano: PNG, JPEG y WebP.
- Límite UAT: archivo `.ats` de hasta 20 MB y descompresión total segura de hasta 40 MB.
- SGI conserva el archivo `.ats` original y el plano materializado.
- Cada reemplazo crea una revisión SGI; la revisión anterior permanece en historial.
- Importar una nueva revisión invalida los vínculos espaciales previos de Puestos y los devuelve a pendiente de revalidación, preservando el resto de su configuración.
- La página ATS muestra metadatos reales del archivo vigente y el plano real contenido en el paquete.
- Descargar devuelve el archivo `.ats` original.

## Configuración > Puestos
- El selector de ubicación usa el plano real del `.ats` vigente.
- El usuario selecciona la ubicación haciendo clic directamente sobre la imagen.
- SGI guarda coordenadas normalizadas `X/Y` en rango `[0,1]` y el `ats_package_id` que originó esa ubicación.
- Una ubicación basada en coordenadas solo es válida contra la revisión ATS vigente del Punto.
- Los campos legacy `ats_location_key/label` se conservan temporalmente por compatibilidad UAT.

## Índice de Riesgo de Punto
- El importador busca el dato si el `.ats` lo entrega explícitamente con una clave reconocida.
- No se infiere ni calcula en SGI.
- Si el paquete no lo contiene, la UI muestra `— / no informado por este archivo .ats`.

## Persistencia
- Nueva tabla `ats_point_package`.
- `post_operational_config` agrega `ats_package_id`, `ats_location_x`, `ats_location_y`.

## Validación con archivo real
Se revisó el paquete aportado `PTO-469F3491_R2 (1).ats`: esquema ATS 2.0, `ATS_PUBLISHED_ARCHITECTURE`, plano PNG 1672×941 incluido en `assets/plans/...`, integridad SHA-256 válida. El paquete de muestra no contiene actualmente un Índice de Riesgo de Punto explícito.
