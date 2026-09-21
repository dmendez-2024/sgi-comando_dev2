# UAT v0.1.5.1 — build fix

Corrección puntual del frontend Dashboard:
- TypeScript infería `regesepResults` como `(0 | 1)[]` y el acumulador de `reduce` como `0 | 1`.
- Se fuerza el acumulador a `number` mediante `reduce<number>(...)`.
- No cambia funcionalidad ni modelo de datos.
