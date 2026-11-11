# PATROL_KM: captura y publicación

## Reglas encontradas en la documentación

- `docs/DECISIONS.md`, SGI-10: breadcrumb opcional con una muestra cada 2 minutos.
- `docs/modules/MODULE_SGI-10_PATRULLAS.md`, Breadcrumb GPS: conservar fecha real,
  latitud, longitud, precisión y ejecución; no sustituye los hitos.
- En SGI Operador, `docs/IMPULSOS_RULES_FROZEN.md`: GPS activo y velocidad <= 5 km/h.
- El contrato SMC del 4 de noviembre pide kilómetros decimales en `value`, no
  bloques enteros ni el resultado del antiguo sorteo por cada 1000 m.
- No se encontró una precisión máxima aprobada ni un tiempo máximo entre muestras.
  La tolerancia de 500 m de PatrolGps no se adopta como regla de recompensa.

## Captura y recepción

Operador inicia un servicio foreground de tipo location al confirmar una patrulla.
La notificación avisa del muestreo cada 2 minutos. Al cerrar la patrulla o cerrar
sesión se detienen los sensores. Si Android mata el proceso no se reinicia a ciegas:
al volver a la pantalla y recuperar una patrulla activa se restablece el servicio.
La ausencia de muestras se refleja en los huecos; no se inventan trayectos.

Las muestras se guardan en SQLite `sgi_patrol_gps.db`, por usuario y ejecución.
FINISH incorpora `gpsSamples` con capturedAt, latitude, longitude, accuracyMeters
y speedMps cuando Android dispone de velocidad. El arreglo se copia al outbox
durable antes de limpiar la captura local. Se usa la interconexión existente de
patrullas: no se requiere inventar otro ID de CORE.

Comando registra el arreglo al cerrar la ejecución, dentro de la misma transacción.
Se deduplica por instante GPS e identidad derivada de instancia/ejecución/fecha.
No se usan lecturas anteriores al START ni posteriores al FINISH. Reintentar un
FINISH ya confirmado conserva el estado y no recalcula recompensas.
Clientes antiguos sin gpsSamples cierran igual, pero no producen kilómetros.

## Política inicial UAT y cálculo

Configuración inicial solicitada el 2026-11-11, ajustable por ambiente:

```dotenv
SGI_PATROL_DISTANCE_MAX_ACCURACY_M=25
SGI_PATROL_DISTANCE_MAX_GAP_SECONDS=180
```

Comando usa esos valores por defecto si no se suministran las variables.
Se conservan 120 segundos de muestreo y 5 km/h, documentados previamente.
Los 25 m son la precisión máxima adoptada para UAT; los 180 s son tolerancia
operacional propuesta para el muestreo, no un umbral encontrado en la documentación
original. Una configuración inválida no genera mediciones ni bloquea el cierre.
Los valores requieren validación en recorridos conocidos y pruebas en reposo;
la precisión Android es una incertidumbre estimada, no un error máximo garantizado.

Con política válida se ordenan muestras por fecha y se suman segmentos usando
Haversine. Se rechaza un tramo con precisión faltante/excesiva, tiempo cero o
negativo, hueco mayor al permitido o velocidad media superior a 5 km/h. Si hay
velocidad reportada por GPS, tampoco debe superar 5 km/h en los extremos.
Una muestra inválida rompe la continuidad: no se salta para unir puntos lejanos.
La velocidad media es una aproximación, no prueba la velocidad instantánea entre
muestras; errores GPS pueden afectar la distancia incluso bajo el umbral de precisión.
No se instala ni requiere PostGIS.

`patrol_distance_measurement` guarda un snapshot inmutable con km, conteos,
política y versión del algoritmo. No se reescribe al cambiar parámetros.
La fuente SMC PATROL_DISTANCE usa measured_at para el cursor y FINISH como
occurredAt; envía PATROL_KM con cantidad decimal. Nunca toca el sorteo local.

Para capturas guardadas antes de definir política, un administrador nacional
puede ejecutar POST /api/v1/integrations/smc/patrol-distance/measure. Procesa hasta
100 ejecuciones por llamada, no cambia mediciones existentes y no envía por sí solo.
El publicador SMC existente detecta los snapshots nuevos en sus ciclos posteriores.
La fecha operacional debe pertenecer a un periodo abierto de SMC.

## Despliegue y pruebas

Primero migración V137/backend; después APK. No publicar la APK contra un backend
que ignore gpsSamples: un FINISH ya cerrado no incorpora muestras al reintentarse.
No se hace backfill desde fotos/hitos históricos ni desde muestras legacy sin sample_id.

Pruebas locales: cálculo con/sin filtros, ausencia de política, idempotencia,
snapshot y conversión a PATROL_KM con PostgreSQL aislado. La captura con pantalla
apagada, permisos revocados, cierre de sesión y FINISH offline requieren UAT en
dispositivo físico. Las reglas de recompensa antiguas siguen pendientes de
reconciliación con SMC; este cambio solo entrega distancia.

## Resultado de validación local

18 pruebas de backend aprobadas, sin fallos ni errores: cálculo, PostgreSQL temporal,
fuentes de Impulsos y transporte/autenticación simulados. No se usó SMC real.
La compilación de Operador se intentó offline y con resolución de dependencias;
se detuvo porque el entorno usa Java 8 y las dependencias requieren una JVM más
reciente. No se generó APK ni se verificó el servicio GPS en dispositivo físico.

## Corrección del cursor SMC (V139)

La fuente PATROL_DISTANCE también requiere ampliar la restricción
smc_source_cursor_source_type_check. V137 no lo hacía; V122 solo admitía las
otras tres fuentes y el publicador fallaba cada 30 segundos con SQLState 23514.
V139 amplía la lista sin reiniciar cursores ni tocar entregas existentes.

La prueba src/test/resources/smc_patrol_distance_cursor_regression.sql reproduce
el fallo sobre V93 + V122 reales y verifica V139 en una base vacía aislada:
cuatro fuentes válidas, conservación de changed_at/last_source_id y rechazo de
una fuente desconocida. Se ejecutó con éxito y terminó con ROLLBACK.
Aplicar V139 en la misma base del backend antes de reanudar la publicación.
