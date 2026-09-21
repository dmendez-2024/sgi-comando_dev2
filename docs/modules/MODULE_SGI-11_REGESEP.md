# SGI-11 — REGESEP v0.1 (En diseño)

## Definición
REGESEP — Reglamento General de Seguridad de Punto.

Es el reglamento operativo, estructurado, vivo y versionado que describe cómo debe protegerse y operar un Punto.

REGESEP NO debe ser un documento editado manualmente como fuente primaria.
Debe componerse automáticamente desde los Systems of Record y configuraciones operativas vigentes.

El PDF/visualización es una representación de una versión estructurada del REGESEP, no el System of Record.

## Unidad normativa
REGESEP pertenece al `Punto`.

Un REGESEP incluye todos los Puestos, protocolos, patrullas y demás reglas operativas aplicables dentro de ese Punto.

## Principio de composición
REGESEP es un `composite regulatory snapshot`.

Fuentes principales:

1. ATS — fuente principal del diseño técnico de seguridad.
2. SIC: COM — identidad comercial/contractual del Servicio, Punto y Puestos.
3. SGI: Comando — configuración operacional vigente.
4. SIC: RRMM — referencias de recursos/equipamiento cuando formen parte de requisitos operativos.
5. CORE — contexto Instancia–País, idioma, zona horaria, formatos y parámetros transversales.

REGESEP referencia versiones exactas de sus fuentes para poder reconstruir qué normativa estaba vigente en cualquier momento.

## ATS como fuente principal técnica
Una parte sustancial del REGESEP proviene de ATS.

ATS conserva el SoR técnico de:
- planos;
- UAP — Unidades a Proteger;
- UTA — Unidades Territoriales de Análisis;
- amenazas;
- vulnerabilidades técnicas;
- riesgos;
- componentes de seguridad;
- ubicación/configuración de componentes;
- coberturas;
- medidas/controles técnicos;
- relaciones entre componentes, UAP y UTA;
- índices técnicos que ATS publique;
- versión `.ats` efectiva.

SGI NO replica ni permite editar esos datos técnicos dentro de REGESEP.

REGESEP incorpora una representación normativa del snapshot ATS efectivo y conserva:
- `ats_package_id`
- `ats_version_id`
- fecha de vigencia
- hash/identificador del paquete si aplica.

Cuando ATS exista plenamente, el flujo será:
`ATS → versión .ats publicada/efectiva → SGI: Comando → REGESEP`

## Contenido propuesto del REGESEP

### 1. Identificación del Punto
Fuente principal: SIC: COM / SGI.
- Cliente
- Servicio
- Punto
- dirección/ubicación
- Compañía responsable
- vigencia
- versión REGESEP

### 2. Diseño Técnico de Seguridad
Fuente principal: ATS.
- plano(s)
- UAP
- UTA
- amenazas
- vulnerabilidades técnicas
- riesgos
- componentes de seguridad
- medidas/controles
- referencias de cobertura
- versión ATS

### 3. Puestos de Seguridad
Fuente: SIC: COM + SGI: Comando.
Por cada Puesto:
- código visible
- nombre
- Formato
- turnos
- FHE
- TIER
- descripción/perfil operativo
- requisitos humanos/materiales configurados
- ubicación base cuando aplique

### 4. Relevo
Fuente: SGI-07.
- reglas de Relevo
- validaciones de Agentes
- inventario a entregar/recibir
- confirmación de Consignas
- reglas de extensión parcial aplicables

### 5. Bitácora / Control de Acceso
Fuente: SGI-09.
Por Puesto:
- Protocolos de Acceso vigentes
- Acreditaciones
- PAX/VHL/CONT
- Ingreso/Egreso/Ambos
- Identificación
- Verificación
- Autorización
- lógica Y/O
- Listas/condiciones cuando sean normativas
- versión exacta de cada Protocolo

### 6. Patrullas
Fuente: SGI-10.
- Patrullas vigentes
- Programada/No Programada
- Cerrada/Abierta
- Puesto responsable
- ventanas/frecuencia
- Ruta e Hitos para Patrullas Cerradas
- Secuencia Estricta/Flexible
- métodos de validación

### 7. Consignas
Fuente: SGI-08.
- todas las Consignas publicadas y vigentes
- fuente normativa/base u operacional/ad hoc
- vigencia
- calendario de aplicación
- prioridad
- versión

### 8. Procedimientos y referencias operativas
Sección extensible para reglas transversales del Punto que sean declaradas normativas por los módulos SGI correspondientes.

No debe convertirse en un campo de texto libre gigante: las reglas deben conservar estructura y fuente.

## Qué NO pertenece al REGESEP
REGESEP no debe convertirse en un repositorio de hechos históricos.

No incluye como contenido normativo:
- registros individuales de Bitácora;
- ejecuciones históricas de Patrulla;
- Relevos ya ejecutados;
- Novedades históricas;
- empleados actualmente asignados;
- inventario actual como listado transaccional.

Sí puede referenciar la regla/configuración que gobierna esos hechos.

## Versionado
`RegesepVersion` es inmutable.

Campos conceptuales:
- `regesep_version_id`
- `point_id`
- `version_number`
- `effective_at`
- `created_at`
- `trigger_type`
- `trigger_source_id`
- `source_snapshot_manifest`
- `previous_version_id`
- `status`
- hash/firma técnica opcional
- auditoría

Cada versión debe conservar un `source_snapshot_manifest` con las versiones exactas de:
- ATS
- Puestos/configuración SGI
- Protocolos de Bitácora
- Patrullas
- Consignas
- otras fuentes normativas.

## Disparadores de nueva versión
Cambios efectivos que deben generar nueva versión REGESEP:

- nueva versión ATS efectiva;
- alta/baja/cambio normativo de Puesto;
- publicación/modificación/desactivación de Protocolo de Acceso;
- publicación/modificación/desactivación de Patrulla;
- publicación/modificación/suspensión/finalización de Consigna;
- cambio de reglas de Relevo cuando sean específicas del Punto;
- otras configuraciones explícitamente marcadas `REGESEP_RELEVANT`.

Los borradores NO versionan REGESEP.

## Generación
REGESEP se recompone a partir de fuentes efectivas.

Principio:
- no editar REGESEP directamente;
- editar/publicar el objeto fuente;
- motor de composición genera nueva versión.

Esto evita contradicciones entre el reglamento y la configuración que realmente ejecutan las aplicaciones.

## Evento de cambio
Cada nueva versión debe guardar:
- qué cambió;
- módulo origen;
- usuario que originó/publicó el cambio;
- timestamp;
- versión anterior/nueva del objeto fuente;
- diff estructurado resumido.

Ejemplo:
`Consigna CON-000214 v3 publicada → REGESEP v41`

## Vigencia y distribución
Solo una versión REGESEP puede estar `VIGENTE` para un Punto en un instante dado.

Versiones anteriores:
- `HISTÓRICA`
- inmutables
- consultables según permiso

Una nueva versión vigente debe estar disponible para:
- SGI: Comando
- SGI: Agente / futuro Operador, en la medida necesaria para ejecución
- SGI: Cliente, según permisos
- Auditoría

## Distribución al Agente
No se recomienda obligar al Agente a leer el REGESEP completo en cada cambio.

El sistema debe distribuir el delta operacional relevante.

Ejemplo:
- nueva Consigna → mostrar/confirmar Consigna;
- nuevo Protocolo de Bitácora → la app ejecuta nueva versión;
- nueva Patrulla → aparece nueva obligación;
- cambio ATS con impacto operativo → mostrar instrucción/delta cuando corresponda.

REGESEP conserva el marco completo; los módulos operativos presentan la acción concreta.

## PDF / representación humana
Cada versión puede renderizarse como PDF.

El PDF debe mostrar al menos:
- Cliente / Servicio / Punto
- código y versión REGESEP
- fecha/hora de vigencia
- versión ATS incorporada
- índice
- secciones estructuradas
- pie con identificador único
- código machine-readable para verificar/consultar la versión

El PDF no reemplaza el modelo estructurado.

## Integración futura ATS
Hasta que ATS esté implementado:
- REGESEP puede contener placeholders/referencias de diseño técnico;
- SGI no debe inventar información técnica que corresponde a ATS.

Cuando ATS esté disponible:
1. importar/recibir `.ats`;
2. validar `instance_country_id`, Punto y versión;
3. registrar snapshot efectivo;
4. incorporar su contenido técnico al REGESEP;
5. generar nueva versión REGESEP;
6. conservar relación exacta ATS ↔ REGESEP.

## SITC — Interconexiones
### ATS → SGI: Comando / REGESEP
- Tipo: paquete `.ats` y/o API/eventos futuros.
- Datos: versión ATS, planos, UAP, UTA, amenazas, vulnerabilidades, riesgos, componentes, medidas, índices técnicos publicados.
- ATS es SoR técnico.

### SIC: COM → REGESEP
- Identidad de Cliente/Servicio/Punto/Puestos y datos comerciales read-only relevantes.

### SGI módulos → REGESEP
- Relevo
- Bitácora
- Patrullas
- Consignas
- configuración normativa de Puestos

### REGESEP → SGI: Cliente / Agente / Operador
- versión vigente;
- representación completa según permiso;
- deltas/instrucciones operativas a través de sus módulos respectivos.

## Decisiones congeladas — alcance, ATS y distribución
- Existe un solo REGESEP vigente por Punto, incluyendo todos sus Puestos.
- Una nueva versión ATS efectiva para el Punto genera automáticamente una nueva versión REGESEP.
- Borradores ATS no generan cambios REGESEP.
- Una Vulnerabilidad operacional reportada en SGI no modifica directamente el capítulo técnico del REGESEP; primero debe conciliarse/incorporarse en ATS y entrar mediante una nueva versión ATS efectiva.
- SGI: Cliente puede consultar el REGESEP vigente y versiones anteriores según permisos.
- SGI: Agente / futuro SGI: Operador puede consultar el REGESEP vigente, pero operacionalmente recibe principalmente los deltas e instrucciones concretas que le afectan.
