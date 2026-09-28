# CORE ↔ SGI: Comando — Modelo Territorial por País

**Documento de transferencia entre proyectos**  
**Proyecto origen:** SGI: Comando — Vertical TER (Territorio)  
**Proyecto destino:** CORE  
**Fecha:** 2026-09-08  
**Estado:** Decisión de arquitectura aprobada / pendiente de implementación en CORE

---

## 1. Objetivo

Definir formalmente qué información territorial debe mantener **CORE** como System of Record (SoR) para cada `instance_country_id`, y cómo **SGI: Comando / TER — Territorio** debe consumir esa información.

La intención es evitar que SGI: Comando mantenga catálogos geográficos propios, mapas estáticos o geometrías duplicadas por país.

---

## 2. Principio arquitectónico

**CORE es el System of Record de la estructura territorial oficial del país.**

SGI: Comando no debe definir por sí mismo si un país utiliza:

- Provincias
- Estados
- Departamentos
- Regiones administrativas
- u otra división territorial equivalente

CORE debe proporcionar esa definición para cada `instance_country_id`.

SGI: Comando únicamente utiliza ese catálogo oficial para construir su estructura operacional:

```text
País
└── Zona
    └── Región
        └── Provincia / Estado / equivalente
            └── Compañía
```

La palabra visible en la UI no debe estar hardcodeada como “Provincia/Estado”.

---

## 3. Información territorial que CORE debe mantener

Por cada `instance_country_id`, CORE debe exponer al menos:

### 3.1 Metadatos del país

```text
country_code
country_name
locale
timezone
```

Estos atributos ya forman parte del contexto general de CORE.

### 3.2 Tipo de subdivisión territorial

CORE debe indicar:

```text
subdivision_type
subdivision_singular
subdivision_plural
```

Ejemplo Ecuador:

```text
subdivision_type     = PROVINCE
subdivision_singular = Provincia
subdivision_plural   = Provincias
```

Ejemplo hipotético para otro país:

```text
subdivision_type     = STATE
subdivision_singular = Estado
subdivision_plural   = Estados
```

SGI: Comando debe usar estos valores para construir dinámicamente sus etiquetas.

---

## 4. Catálogo oficial de subdivisiones

CORE debe mantener el catálogo territorial oficial del país.

Cada subdivisión debería incluir como mínimo:

```text
subdivision_id
instance_country_id
official_code
name
status
```

Ejemplo para Ecuador:

```text
EC-G  Guayas
EC-P  Pichincha
EC-A  Azuay
...
```

Para Ecuador, CORE debe contener las 24 provincias como catálogo oficial.

SGI: Comando no debe crear, renombrar ni eliminar provincias.

---

## 5. Polígonos geográficos

CORE debe mantener también la geometría oficial de cada subdivisión territorial.

Por cada Provincia / Estado / equivalente:

```text
subdivision_id
geometry
geometry_version
source
effective_from
```

La geometría puede implementarse técnicamente como:

- GeoJSON
- PostGIS Geometry / MultiPolygon
- u otro formato geoespacial compatible

La elección tecnológica queda a CORE / arquitectura, pero SGI debe poder consumir los polígonos.

---

## 6. Versionamiento territorial

CORE debería versionar su dataset territorial.

Ejemplo:

```text
territorial_dataset_version = EC-2026.01
```

Esto permite:

- trazabilidad
- reproducibilidad histórica
- cambios oficiales de límites
- cambios de nombre
- creación o eliminación oficial de subdivisiones
- sincronización entre plataformas

SGI: Comando debería conservar la versión del dataset CORE que utilizó para una determinada configuración territorial.

---

## 7. Responsabilidad de SGI: Comando / TER

SGI: Comando no administra geografía oficial.

TER administra únicamente la **clasificación operacional** de las subdivisiones oficiales.

Ejemplo:

```text
Guayas      → Zona Sur → R-S1
Santa Elena → Zona Sur → R-S1
El Oro      → Zona Sur → R-S3
Pichincha   → Zona Norte → R-N1
```

SGI guarda relaciones como:

```text
subdivision_id → zone_id
subdivision_id → region_id
```

pero el polígono continúa perteneciendo a CORE.

---

## 8. Reglas de Zonas y Regiones en SGI

### 8.1 Zona

Una Zona:

- pertenece a una `instance_country_id`
- agrupa una o más subdivisiones oficiales del país
- tiene código
- nombre
- estado
- responsable
- historial
- puede contener una o más Regiones

### 8.2 Región

Una Región:

- pertenece exactamente a una Zona
- solo puede utilizar subdivisiones previamente incluidas en su Zona
- tiene código
- nombre
- estado
- responsable
- historial

### 8.3 Regla de exclusividad

En la versión actual de TER:

> Una Provincia / Estado completa pertenece a una sola Zona y a una sola Región operacional a la vez.

No se divide una Provincia / Estado entre dos Regiones.

Si en el futuro Cajamarca necesita territorios subprovinciales, deberá diseñarse una capacidad distinta.

---

## 9. Mapa dinámico en SGI: Comando

El mapa de Territorio no debe ser una imagen estática.

CORE entrega los polígonos.

SGI conoce la asignación:

```text
Provincia → Zona → Región
```

y el frontend construye el mapa dinámicamente.

### 9.1 Comportamiento esperado

Si una provincia cambia:

```text
Bolívar
R-S1 → R-S2
```

SGI no debe cargar una nueva imagen.

El mapa debe repintarse automáticamente utilizando:

```text
polígono CORE
+
configuración Zona/Región SGI
```

---

## 10. Convención visual sugerida

En SGI: Comando:

- color principal por Zona
- variación de tonalidad o borde por Región
- borde fino = Provincia / Estado
- borde medio = Región
- borde fuerte = Zona

Tooltip sugerido:

```text
Guayas
Zona Sur
Región R-S1
3 Compañías
18 Puntos activos
```

La información operacional adicional proviene de SGI; la geometría proviene de CORE.

---

## 11. Interfaz CORE → SGI

Contrato conceptual recomendado:

```text
GET /core/instance-countries/{instance_country_id}/territorial-structure
```

Respuesta conceptual:

```json
{
  "instanceCountryId": "...",
  "countryCode": "EC",
  "countryName": "Ecuador",
  "subdivisionType": "PROVINCE",
  "subdivisionSingular": "Provincia",
  "subdivisionPlural": "Provincias",
  "datasetVersion": "EC-2026.01",
  "subdivisions": [
    {
      "id": "...",
      "code": "EC-G",
      "name": "Guayas",
      "status": "ACTIVE",
      "geometry": "..."
    }
  ]
}
```

El formato exacto puede variar, pero estos conceptos deben conservarse.

---

## 12. Integraciones relacionadas

### CORE → SGI: Comando / TER

Envía:

- tipo de subdivisión territorial
- singular y plural
- catálogo oficial
- códigos
- nombres
- estados
- polígonos
- versión del dataset territorial

### SIC: RRHH → SGI: Comando / TER

Envía / referencia:

- personal elegible
- responsables de Zona
- responsables de Región

### SGI: Comando / TER

Mantiene:

- Zonas
- Regiones
- asignación de subdivisiones a Zona
- asignación de subdivisiones a Región
- responsable operacional
- historial/auditoría

---

## 13. Impacto en UI

SGI no debe mostrar etiquetas genéricas como:

```text
Provincia / Estado
```

Debe mostrar dinámicamente:

```text
Provincias
```

o:

```text
Estados
```

según la configuración proveniente de CORE.

Ejemplo Ecuador:

```text
+ Provincia
Provincias de Zona Sur
Provincias de R-S1
```

---

## 14. Impacto SITC

Registrar formalmente la interconexión:

```text
CORE
  ↓
Catálogo territorial oficial + geometrías
  ↓
SGI: Comando / TER
  ↓
Clasificación operacional Zona / Región
```

SoR:

```text
CORE = geografía oficial
SGI = estructura operacional
```

No debe existir duplicación de autoridad sobre los mismos datos.

---

## 15. Decisión congelada

**TER-DEC-CORE-GEO-001**

> CORE es el System of Record del tipo de división territorial, catálogo oficial y polígonos geográficos de las subdivisiones administrativas de cada país. SGI: Comando / TER únicamente asigna esas subdivisiones oficiales a Zonas y Regiones operacionales. El mapa de Territorio se genera dinámicamente usando los polígonos proporcionados por CORE y la configuración Zona/Región almacenada por SGI.

**Estado:** APROBADA

---

## 16. Criterios de aceptación para CORE

CORE no se considera completo para esta integración hasta cumplir al menos:

- [ ] definir tipo de subdivisión por `instance_country_id`
- [ ] definir singular y plural para UI
- [ ] mantener catálogo oficial
- [ ] mantener códigos estables
- [ ] mantener estado de cada subdivisión
- [ ] mantener geometría/polígono
- [ ] versionar dataset territorial
- [ ] exponer API/contrato consumible por SGI
- [ ] soportar Ecuador con sus 24 provincias
- [ ] documentar System of Record y versionamiento

---

## 17. Criterios de aceptación para SGI: Comando / TER

- [ ] no mantiene catálogo territorial propio como SoR
- [ ] consume tipo de subdivisión desde CORE
- [ ] usa etiquetas dinámicas
- [ ] consume catálogo oficial
- [ ] consume polígonos
- [ ] asigna subdivisiones a Zonas
- [ ] asigna subdivisiones a Regiones
- [ ] Región solo usa subdivisiones de su Zona
- [ ] mapa se repinta dinámicamente
- [ ] cambio de Región no requiere una nueva imagen/mapa
- [ ] guarda versión de dataset CORE utilizada
- [ ] mantiene auditoría de cambios operacionales

---

## 18. Nota de transferencia al chat de CORE

Este documento debe incorporarse al Decision Log / Architecture de CORE.

No es una propuesta aislada de UI: define una **responsabilidad transversal de CORE** que deberá poder ser consumida por SGI: Comando y potencialmente por otros sistemas Cajamarca que necesiten contexto territorial oficial.