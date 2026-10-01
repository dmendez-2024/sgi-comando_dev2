# SGI: Comando — NEX v0.1 FROZEN

Baseline acumulativa: `SGI_Comando_CSL_v0.2.5_Reasignacion_UAT.zip`.

## Alcance

Se incorpora **Nexus — Motor de Eventos y Reglas Operativas** dentro de:

`Operaciones > Servicios > Configuración del Punto > Checklist de configuración`

Nexus aparece inmediatamente después de **Consignas** y antes de **Recursos Humanos**.

## NEX v0.1

La página Nexus se limita deliberadamente a dos vistas:

1. **Reglas** — listado por código, nombre, relación, evento objetivo, evento de referencia, estado, versión y última modificación; incluye `+ Nueva regla`.
2. **Historial** — trazabilidad de creación y modificación de reglas.

El editor de una regla permite configurar:
- código automático `NEX-####`;
- nombre, descripción, versión y estado;
- aplicación permanente o por horario/días;
- **Evento objetivo**: Objeto + Evento/Estado;
- relación temporal: **ANTES / DURANTE / DESPUÉS**;
- ventana temporal opcional;
- **Evento de referencia**: Objeto + Evento/Estado;
- correlación opcional entre eventos;
- acciones opcionales si la relación se cumple o no se cumple.

Ejemplo modelado en la UAT:

`Barrera Vehicular / Apertura` **DESPUÉS DE** `Registro Vehicular / Registrado`, máximo 5 minutos, correlando misma placa, Punto y acceso.

## Estado técnico

- Frontend UAT funcional.
- Datos Nexus: DEMO/local con persistencia en `localStorage` por Punto.
- No se implementa todavía el runtime EVC/BPM productivo.
- No se agregan endpoints backend, tablas, migraciones Flyway ni interconexiones.
- Backend, database y SITC permanecen heredados de CSL v0.2.5.

## Freeze

Estado: **FROZEN** desde 2026-09-27 para entrega a SISTEMAS. El freeze valida esta baseline funcional/UI; runtime EVC/BPM y persistencia backend productivos siguen pendientes y requieren una versión posterior.
