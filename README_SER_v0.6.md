# SGI: Comando — SER v0.6

## Alcance
SER v0.6 incorpora la primera versión funcional de **Configuración → Bitácora** dentro de la Configuración del Punto.

## Bitácora
- Configuración por **Puesto**.
- Protocolos con código automático `PRO-BA-####`.
- Objeto: `PAX / VHL / CONT`.
- Aplicación: `Ingreso / Egreso / Ambos`.
- Secciones: Definición, Identificación, Verificación, Autorización, Evidencias, Captura, Listas y Trazabilidad.
- Lógica `ALL / ANY` en Identificación y Verificación.
- Campos predefinidos y opción **Agregar campo** para campos personalizados.
- Cada campo puede exigir evidencia y asociar una **foto estándar real**.
- Foto estándar: JPG / PNG / WebP, máximo 5 MB, con versión y notas del estándar.
- Preparación explícita para futura integración **VISINT** sin ejecutar comparación automática todavía.
- Guardar borrador y Publicar protocolo.

## Límites de esta versión
- Modelos de Compañía y Estándares Cajamarca se muestran pero quedan pendientes de funcionalidad.
- Historial detallado de versiones se deja pendiente; `version_no` avanza al publicar revisiones vigentes.
- Esta vertical configura reglas. La Bitácora ejecutada permanece fuera de alcance hasta desarrollar **Operación**.
