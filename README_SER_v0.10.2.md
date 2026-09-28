# SER v0.10.2 — UAT Hotfix

Este paquete conserva el stack completo de SGI: Comando y corrige el error HTTP 500 detectado al crear una nueva Acreditación después de eliminar acreditaciones intermedias de un Protocolo de Bitácora.

## Base
- SER v0.10.1
- COM v1.1.3 FROZEN
- ASI v0.7.4

## Hotfix
La numeración `ACC-###` ya no se calcula con `cantidad actual + 1`. Ahora avanza desde el mayor sufijo numérico existente dentro del Protocolo, evitando colisiones con códigos que ya existen.

El límite funcional sigue siendo **máximo 10 acreditaciones concurrentes por Protocolo de Bitácora**.

## Arranque
1. Ejecutar `repo/scripts/uat-start.ps1` desde la carpeta UAT externa.
2. Esperar `Backend READY.`
3. Ejecutar `repo/scripts/uat-open.ps1`.

## URLs
- UI: http://localhost:5173
- API/Backend: http://localhost:8080

No abrir `http://localhost:8080` como interfaz: ese puerto corresponde al backend.
