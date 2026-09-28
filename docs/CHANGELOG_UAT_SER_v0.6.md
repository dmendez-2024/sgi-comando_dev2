# SGI: Comando — CHANGELOG UAT SER v0.6

## Nueva subpágina: Configuración → Bitácora
- Mantiene la línea gráfica clara vigente de SGI: Comando.
- Configuración por Puesto y Protocolo.
- Editor por pasos: Definición, Identificación, Verificación, Autorización, Evidencias, Captura, Listas y Trazabilidad.
- Objetos PAX / VHL / CONT y aplicación Ingreso / Egreso / Ambos.
- Lógica ALL / ANY en Identificación y Verificación.
- Campos institucionales y campos personalizados con **Agregar campo**.
- Evidencia, modo de captura y Foto estándar por campo.
- Upload de Foto estándar JPG / PNG / WebP hasta 5 MB.
- Preparación explícita para VISINT; la comparación automática aún no se ejecuta.
- Guardar borrador y Publicar protocolo.

## Backend / Base de datos
- `logbook_protocol`.
- `logbook_protocol_field`.
- API `/api/bitacora/*` para protocolos, campos e imágenes estándar.

## Fuera de alcance
- Ejecución real de Bitácora / vista Operación.
- Modelos de Compañía y Estándares Cajamarca funcionales.
- Historial documental completo de versiones.
