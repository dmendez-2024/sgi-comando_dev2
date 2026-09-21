# SGI: Comando — ASI v0.7.2

UAT fix sobre ASI v0.7.1.

- Corrige la aceptación inmediata de transferencias de Compañía cerrando y haciendo flush de la membresía PRIMARY vigente antes de insertar la nueva membresía.
- Sustituye la confirmación nativa del navegador por un modal SGI para Aceptar / Rechazar / Anular.
- Los errores de backend se muestran dentro del modal de decisión.
- No modifica la lógica funcional ni agrega migraciones de base de datos.
