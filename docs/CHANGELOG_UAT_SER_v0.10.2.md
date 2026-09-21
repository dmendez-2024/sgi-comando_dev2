# SGI: Comando — SER v0.10.2 UAT Hotfix

**Fecha:** 2026-09-19  
**Base:** SER v0.10.1 + COM v1.1.3 FROZEN + ASI v0.7.4

## Corrección
Se corrige un error HTTP 500 al crear una nueva Acreditación de Bitácora cuando el protocolo tenía acreditaciones eliminadas previamente y, por tanto, huecos en la numeración de códigos.

### Causa raíz
`nextAccreditationCode()` calculaba el siguiente código como `cantidad actual + 1`.

Ejemplo real de UAT:
- existen 7 acreditaciones;
- entre sus códigos ya existen `ACC-008`, `ACC-009` y `ACC-010`;
- el algoritmo anterior intentaba crear `ACC-008` porque `7 + 1 = 8`;
- la restricción única `(instance_country_id, protocol_id, code)` rechazaba el duplicado y la API respondía 500.

### Solución
El próximo código se calcula desde el mayor sufijo numérico existente dentro del protocolo.

Ejemplo:
- 7 acreditaciones activas en el borrador;
- mayor código existente: `ACC-010`;
- la siguiente acreditación recibe `ACC-011`;
- el contador funcional sigue siendo `8/10`.

## Regla funcional preservada
El máximo continúa siendo **10 acreditaciones concurrentes por Protocolo de Bitácora**. La numeración del código no representa la cantidad actual; representa identidad y no debe reciclarse por borrado.

## Regresión requerida
1. Crear protocolo de Bitácora.
2. Crear varias acreditaciones.
3. Eliminar una acreditación intermedia.
4. Crear otra acreditación.
5. Confirmar que no se reutiliza un código existente.
6. Llegar a 10 acreditaciones concurrentes.
7. Confirmar que la acreditación número 11 se bloquea con HTTP 400 y mensaje funcional, no con HTTP 500.
