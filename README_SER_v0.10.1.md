# SER v0.10.1 — UAT

Este paquete conserva el stack completo de SGI: Comando y no es un servidor estático independiente.

## Arranque
1. Ejecutar `repo/scripts/uat-start.ps1` desde la carpeta UAT externa.
2. Esperar `Backend READY.`
3. Ejecutar `repo/scripts/uat-open.ps1`.

## URLs
- UI: http://localhost:5173
- API/Backend: http://localhost:8080

No abrir `http://localhost:8080` como interfaz: ese puerto corresponde al backend.
