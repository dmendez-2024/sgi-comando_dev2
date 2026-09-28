
# Taxonomía Maestra de Tareas / Novedades Cajamarca

## Principio
`TAREA` es el paraguas funcional para todo aquello que debe hacerse o reportarse en Cajamarca.

Familias maestras:
1. Consignas
2. Hallazgos
3. Vulnerabilidades
4. Incidentes
5. Requerimientos
6. Actividades

Todo evento puede originarse en interfaces SGI y converge en SGI: Comando. Desde allí se enruta según la familia y el System of Record/gestor correspondiente.

## Actores/canales de creación
Pueden generar elementos:
- Agente → SGI: Agente.
- Supervisor → SGI: Supervisor.
- Cliente → SGI: Cliente.
- Coordinador de Compañía → SGI: Comando.
- Asistente de Coordinación → SGI: Comando.
- Futuro Agente/Supervisor → SGI: Operador.

## 1. Consignas
Definición funcional: instrucciones/tareas explícitas que el guardia debe ejecutar o cumplir durante el servicio.

Subtipos operacionales maestros:
- Relevo.
- Bitácora.
- Patrulla.
- Consignas Ad Hoc.

SoR/gestión:
- Permanece en SGI: Comando.
- Se integra en REGESEP y lo versiona.
- Se distribuye a interfaces operativas.
- Requiere confirmación cuando aplica.

## 2. Hallazgos
Definición: situaciones observadas por personal operativo que requieren atención pero no constituyen necesariamente un incidente de seguridad.

Subtipos:
- Orden.
- Limpieza.
- Mantenimiento.
- Estacionamiento.
- Componentes de Seguridad.

### Componentes de Seguridad
Casos:
- nueva cámara observada/instalada;
- cámara previamente dañada que fue reparada;
- nuevo cerco/equipo/componente;
- cambio de estado de un componente previamente asociado a una Vulnerabilidad.

SoR/gestión:
- Permanece en SGI: Comando.
- Debe poder vincularse a una Vulnerabilidad previa.
- Debe quedar preparado para futura conciliación con ATS / `.ats` cuando ATS exista.

## 3. Vulnerabilidades
Definición: puntos débiles o riesgos identificados en las instalaciones/operación.

Subtipos:
- Acceso.
- Perímetro.
- Interno.

SoR/gestión:
- Permanece en SGI: Comando.
- Puede relacionarse con Hallazgos de Componentes de Seguridad y, a futuro, con ATS.

## 4. Incidentes
Definición: problemas o eventos que requieren resolución.

Subtipos:
- Servicio.
- Seguridad.
- Administrativo.

Routing:
- Se captura/origina en SGI.
- Ingresa a SGI: Comando.
- Se enruta a STC (Sistema de Tareas Cajamarca) para gestión del trabajo/resolución.

Arquitectura recomendada:
- SGI conserva el registro/evento operacional de origen y su contexto de seguridad.
- STC administra el ciclo de trabajo: asignación, responsable, SLA, progreso, cierre.
- STC devuelve estado/resultado a SGI: Comando.
Esta recomendación queda pendiente de congelar.

## 5. Requerimientos
Definición: peticiones de clientes o terceros que deben evaluarse y, si corresponde, cumplirse.

Routing:
- Origen SGI → SGI: Comando → STC.

Arquitectura recomendada:
- SGI conserva origen/contexto.
- STC gestiona resolución.
- STC devuelve estado/cierre.
Pendiente de congelar.

## 6. Actividades
Definición: tareas internas de la empresa en las que jefes delegan trabajos a subordinados.

Routing:
- Origen SGI → SGI: Comando → STC.

Arquitectura recomendada:
- STC es gestor del ciclo de trabajo.
- SGI conserva referencia contextual si la Actividad nació en operación de seguridad.
Pendiente de congelar.

## Matriz de routing
| Familia | Entra por | Converge en | Se queda en SGI: Comando | Va a STC | Futuro ATS |
|---|---|---|---:|---:|---:|
| Consignas | SGI Cliente/Agente/Supervisor/Comando/Operador | SGI: Comando | Sí | No | Contexto indirecto |
| Hallazgos | mismos canales | SGI: Comando | Sí | No | Componentes de Seguridad: sí, conciliación futura |
| Vulnerabilidades | mismos canales | SGI: Comando | Sí | No | Sí, futura relación |
| Incidentes | mismos canales | SGI: Comando | Origen/contexto recomendado | Sí | No directo |
| Requerimientos | mismos canales | SGI: Comando | Origen/contexto recomendado | Sí | No |
| Actividades | mismos canales | SGI: Comando | Referencia recomendada | Sí | No |

## Auditoría común
Todos los tipos deben conservar:
- `instance_country_id`
- actor/canal origen
- fecha/hora
- Compañía
- Cliente/Servicio/Punto/Puesto cuando aplique
- descripción/evidencia
- autor
- estado
- relaciones con entidades vinculadas
- historial de cambios
- destino/routing
- identificador externo si se crea objeto en STC/ATS futuro

## Novedades — definición congelada
`Novedades` NO es el paraguas general de Tareas ni una entidad de dominio independiente.

Es un conjunto funcional de Tareas que ameritan la atención del cliente y se muestran en SGI: Cliente:

- Hallazgos
- Vulnerabilidades
- Incidentes

Por tanto:

`NOVEDADES = HALLAZGOS + VULNERABILIDADES + INCIDENTES`

No forman parte de Novedades:
- Consignas
- Requerimientos
- Actividades

La clasificación `Novedades` puede utilizarse como agrupador de presentación, filtros, contadores y vistas para SGI: Cliente, sin duplicar las entidades subyacentes.

## Diseño de Hallazgos y Vulnerabilidades
- Hallazgos y Vulnerabilidades permanecen en SGI: Comando.
- `Novedades` en SGI: Cliente es una vista agregada de Hallazgos + Vulnerabilidades + Incidentes.
- Hallazgos: Orden, Limpieza, Mantenimiento, Estacionamiento, Componentes de Seguridad.
- Vulnerabilidades: Acceso, Perímetro, Interno.
- Hallazgo y Vulnerabilidad son entidades separadas y pueden vincularse.
- `Componentes de Seguridad` prepara conciliación futura con ATS.
