# Taxonomía de Incidentes — referencia vigente

Fuente: `Incidentes(1).xlsx` suministrado por el usuario el 2026-09-27, hoja `Incidentes`.

**Jerarquía canónica para esta UAT:** `Categoría → Subcategoría → Incidente`.

La fuente contiene **3 categorías, 20 subcategorías y 90 tipos de incidente**.

> Corrección respecto de CSL v0.2: `Inasistencia programada` e `Inasistencia efectiva` NO son subcategorías. Son tipos de incidente bajo `Incidentes de Servicio → Asistencia y Puntualidad`.

## Incidentes de Servicio

### Asistencia y Puntualidad
- Relevo tardío
- Inasistencia programada
- Inasistencia efectiva
- Abandono del puesto

### Presentación Personal
- Mala higiene
- Uniforme incompleto
- Uniforme deteriorado
- Equipamiento incompleto

### Disciplina
- Distracción general
- Uso indebido de celular
- Conducta inapropiada
- Dormido en puesto
- Consumo de alcohol o drogas

### Cumplimiento Operativo
- Patrulla incompleta
- Patrulla no ejecutada
- Bitácora incompleta
- Control de acceso incorrecto
- Falta de registro de novedades
- Falta de notificación / comunicación
- Falta de cumplimiento de consignas

### Competencia Profesional
- Error operativo
- Uso incorrecto del equipo
- Desconocimiento del procedimiento

### Atención al Cliente
- Mala atención
- Falta de cortesía
- Queja del cliente
- Comunicación deficiente

## Incidentes de Seguridad

### Delitos
- Robo
- Hurto
- Asalto
- Fraude
- Vandalismo
- Daño a la propiedad

### Accesos No Autorizados
- Intrusión
- Persona sospechosa
- Suplantación
- Violación del perímetro

### Emergencias Médicas
- Accidente
- Desmayo
- Lesión
- Muerte
- Enfermedad súbita

### Incendios y Riesgos
- Incendio
- Conato
- Fuga de gas
- Derrame químico
- Explosión

### Seguridad Física
- Violencia
- Pelea
- Amenaza
- Agresión
- Arma encontrada
- Objeto sospechoso

### Desastres Naturales
- Inundación
- Sismo
- Tormenta
- Deslizamiento

### Riesgos Operacionales
- Fallas en Seguridad Electronica
- Vulnerabilidad detectada

### Infraestructura
- Daño en Puesto de Trabajo
- Daño Fuera de Puesto de Trabajo

## Incidentes Administrativos

### Talento Humano
- Vacaciones pendientes
- Descanso incumplido
- Nómina
- Horas extras
- Contrato
- Documentación

### Logística MARE
- Uniformes
- Equipamiento
- Radios
- Linternas
- Armas
- Municiones

### Logística Vehículos
- Moto fuera de servicio
- Vehículo averiado
- Mantenimiento pendiente
- Accidente vehicular

### Tecnología
- Aplicación caída
- Error del SGI
- Problema GPS
- Internet
- Equipo móvil

### Documentacion
- Permiso vencido
- Licencia
- Auditoría
- Hallazgo de inspección

### Comercial
- Solicitud del cliente
- Cambio de servicio
- Facturación
- Reclamo administrativo

## Reglas de uso en Consola

- El usuario selecciona primero Categoría, luego Subcategoría y finalmente Incidente.
- El flujo especial de reasignación se activa exclusivamente cuando el tipo de incidente es `Inasistencia programada` o `Inasistencia efectiva`.
- Los textos del catálogo se conservan exactamente como aparecen en el Excel de referencia.
- Esta versión sigue siendo UAT frontend/local; la persistencia productiva del catálogo requiere su vertical backend correspondiente.
