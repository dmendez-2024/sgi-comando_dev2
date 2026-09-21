# SGI: Comando — SER v0.7.1

UAT fix de SER v0.7. Mantiene Patrullas y el patrón transversal de versionado aprobado.

## Correcciones
- Corrige Flyway V14 separando las tablas de Configuración de Patrullas (`patrol_config_*`) de las tablas de Patrullas ya existentes en el baseline.
- `uat-start.ps1` espera a que Quarkus responda READY antes de continuar.

## Sin cambio funcional
Se conserva `Punto → Puesto → Protocolo → Patrulla → Reglas`, configuración ATS/Campo/Mixta, fotos estándar y publicación versionada inmutable.
