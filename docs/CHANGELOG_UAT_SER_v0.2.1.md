# SGI: Comando — CHANGELOG UAT SER v0.2.1

## Corrección de compilación
- Se corrige colisión de nombre entre el icono `Map` de `lucide-react` y el constructor nativo `Map` de JavaScript/TypeScript.
- El icono se importa ahora como `MapIcon`, preservando el uso tipado de `new Map<string, PointRow>()`.
- Esto elimina los errores TypeScript TS7009, TS2558 y los errores derivados de parámetros implícitamente `any`.
- Sin cambios funcionales ni visuales adicionales respecto de SER v0.2.
