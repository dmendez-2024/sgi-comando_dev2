# SGI: Comando — SER v0.10.3.1 UAT Hotfix

Hotfix de entrega/UAT sobre SER v0.10.3.

- Conserva los cambios funcionales de SER v0.10.3: alcance multi-Puesto de Protocolos de Bitácora, todos los Protocolos del Punto visibles, estado contextual Inactivo cuando no aplica al Puesto, tipografía y espaciado del panel derecho.
- Corrige trazabilidad visual: Sidebar muestra `SER v0.10.3.1`.
- Agrega `public/uat-version.json` para verificación automática de versión servida.
- `uat-start.ps1` valida que el frontend realmente servido corresponda a SER v0.10.3.1 y, si detecta frontend anterior, fuerza rebuild sin caché del frontend.
- `uat-open.ps1` abre el navegador con query de cache-busting.
- Nginx UAT envía headers `no-store/no-cache` para evitar que el navegador reutilice una SPA anterior.
