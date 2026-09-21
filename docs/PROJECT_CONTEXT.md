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

### COM — Compañías v1.1.3 FROZEN

CORE es SoR de identidad de Compañía (Nombre, Logo, Reseña histórica). SGI activa la Compañía y mantiene Estado/Zona/Regiones operativas. Kaibil es la Compañía de coordinación, siempre Activa.

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


## SER v0.9 — ingreso y asignación de Servicios
SIC: COM entrega `Servicio = Cliente + Punto` y 1..n Puestos, pero no determina Compañía operativa. El Servicio entra con asignación pendiente y se presenta en la bandeja lógica Kaibil. Coordinación asigna la Compañía según ámbito; solo después se habilita Configuración del Punto.

## Contrato SIC: RRHH
El personal que ingresa a SGI debe venir de SIC: RRHH ya adscrito a **Seguridad Física (SF) + Compañía**. SIC: RRHH permanece SoR de esa relación.


## SER v0.9.1 — retiro y reasignación de Servicios
- Coordinación autorizada puede retirar un Servicio asignado hacia la bandeja lógica Kaibil según alcance territorial.
- La configuración operacional permanece ligada al Punto y no se borra/copia al cambiar de Compañía.
- Solo las asignaciones futuras desaparecen de planificación activa; histórico y turno en curso se conservan.
- `operational_transition_until` protege el cierre del turno heredado y evita doble cobertura en la nueva Compañía.
- Desde Kaibil se reasigna directamente a otra Compañía autorizada sin aceptación del Coordinador destino.

## Flujo SGI: Operador / VISINT / Impulsos

Queda definida en SGI: Comando la lógica transversal para evidencias visuales de tareas ejecutadas desde SGI: Operador. Las fotografías viajan primero a SGI: Comando; SGI: Comando las remite a VISINT, recibe su evaluación y, únicamente si el resultado habilita la recompensa, ejecuta la regla probabilística de Impulsos. VISINT valida calidad visual; **SGI: Comando decide y registra Impulsos**.

La UI congelada de SGI: Comando no cambia por esta definición. Los mockups de Operador y el contrato completo están en `docs/SGI_OPR_VISINT_IMPULSOS.md`.
