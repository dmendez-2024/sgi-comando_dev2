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

## Política pendiente y cálculo

Se deben definir expresamente ambas variables del ambiente:

```
SGI_PATROL_DISTANCE_MAX_ACCURACY_M=<metros aprobados>
SGI_PATROL_DISTANCE_MAX_GAP_SECONDS=<segundos aprobados>
```

Vacías o inválidas: se guardan muestras sin crear un hecho PATROL_KM. No se
detiene el cierre operativo ni se eligen números de negocio por defecto.

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
