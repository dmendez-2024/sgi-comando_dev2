# COM v1.0 FROZEN — Contexto funcional y técnico

## Qué representa una Compañía

Una Compañía es una unidad operacional de mando/ejecución de Cajamarca dentro de una Instancia–País. Puede operar en varias Regiones, pero siempre dentro de una única Zona.

## Relaciones esenciales

```text
instance_country_id
        ↓
      Zona
        ↓
    Compañía
     ↙   ↓   ↘
Región A Región B Región C
```

## Identificadores

- PK interna: UUID `company_id`.
- Código humano: `COM-###`.
- Ambos permanecen estables durante toda la vida de la Compañía.

## Estado

Estados operacionales principales:

- `ACTIVE` → Activa
- `INACTIVE` → Inactiva

Una Compañía Inactiva conserva historia y puede reactivarse.

## Versionamiento

Cada modificación autorizada genera una nueva `CompanyVersion` con:

- número de versión
- tipo de cambio
- motivo cuando exista
- usuario
- fecha/hora efectiva
- snapshot JSON

## Logo

UAT actual: adapter local.  
Producción: MinIO; no BLOB/Data URL permanente en PostgreSQL.

## Reseña histórica

- Campo libre opcional.
- Máximo 750 caracteres.
- Versionada junto con el resto de la ficha.

## Multi-región / Zona única

- Una Compañía puede agregar Regiones de su Zona aun estando Activa.
- No puede contener Regiones de dos Zonas.
- Retirar una Región requiere 0 Servicios activos de la Compañía en esa Región.
- Para cambiar de Zona primero debe poder retirar todas las Regiones actuales.

## Inactivación

Solo se permite cuando Servicios activos asociados = 0.

El usuario debe primero migrar/finalizar Servicios desde SER.

## Dependencias

- TER v1.0 FROZEN: territorio operacional.
- SER: Servicios activos por Compañía/Región.
- MinIO: Logo en producción.
- PostgreSQL 17: persistencia y versiones.
- CORE indirectamente a través de TER para geografía oficial.

## Scope guard

COM no modifica la lógica funcional de TER ni de otras verticales. Cualquier cambio futuro que requiera alterar una vertical congelada debe versionarse explícitamente y pasar regresión.
