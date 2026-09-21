# ASI v0.3 — Scope Guard

## Regla
ASI v0.3 modifica solo Asignaciones y un componente de shell compartido estrictamente informativo para mostrar el ledger de versiones.

## Verticales congeladas protegidas
- TER v1.0 FROZEN: sin cambios funcionales.
- COM v1.0 FROZEN: sin cambios funcionales.

## Archivos funcionales permitidos
- `frontend/src/pages/Assignments.tsx`
- `frontend/src/styles.css` bajo `.assignments-asi-v03` y estilos del ledger de versiones
- `frontend/src/components/Sidebar.tsx` exclusivamente para ledger informativo

## No permitido
No se modifican páginas, APIs, entidades, migraciones o reglas de negocio de TER/COM.
