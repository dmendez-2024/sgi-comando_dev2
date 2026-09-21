# COM v1.1.3 FROZEN — Contexto funcional y técnico

## Qué representa una Compañía

Una Compañía es una unidad operacional de mando/ejecución dentro de una Instancia–País. **CORE es SoR de su identidad** (Nombre, Logo, Reseña histórica). SGI: Comando activa esa identidad y mantiene Estado, Zona y Regiones operativas. Puede operar en varias Regiones, pero siempre dentro de una única Zona.

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

El Logo proviene de CORE y es **solo lectura** en SGI. UAT conserva snapshot/adaptador local; producción debe consumir la integración formal CORE y almacenar binarios según la arquitectura definida.

## Reseña histórica

- Proviene de CORE.
- Máximo 750 caracteres en el contrato actual.
- Solo lectura en SGI.
- El snapshot operacional conserva trazabilidad de la versión CORE consumida.

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
- CORE directamente para identidad de Compañía y, a través de TER, para geografía oficial.

## Scope guard

COM no modifica la lógica funcional de TER ni de otras verticales. Cualquier cambio futuro que requiera alterar una vertical congelada debe versionarse explícitamente y pasar regresión.
