# UAT Test Plan — v0.4

## A. Arranque
- [ ] Docker build frontend PASS.
- [ ] Docker build backend PASS.
- [ ] PostgreSQL Healthy / Flyway V7 aplicada.
- [ ] Frontend / backend / MinIO arriba.

## B. Territorio
- [ ] Con Presidente/Director Nacional se ven ZN y ZS y las 6 Regiones UAT.
- [ ] Crear Zona en Borrador.
- [ ] Crear Región dentro de Zona.
- [ ] Asignar Responsable desde SIC: RRHH LOCAL.
- [ ] Asignar Provincias a Zona.
- [ ] Región solo permite Provincias pertenecientes a su Zona.
- [ ] Activar objeto y comprobar que Eliminar queda bloqueado.
- [ ] Mapa operacional visible.
- [ ] Compañías muestra Región/Zona y nueva Compañía exige Región.
- [ ] Director Zonal ve solo su Zona; Jefe Regional su Región; Coordinador su Compañía.

## C. Asignaciones — Borrador
- [ ] Coordinador/Asistente ven Guardar borrador y Publicar.
- [ ] Director/Jefe Regional no pueden editar.
- [ ] Guardar borrador registra fecha/usuario.
- [ ] Asignaciones persisten al recargar.
- [ ] Eliminar asignación no solicita confirmación.

## D. Elegibilidad y tarjetas
- [ ] Click en Agente evalúa y marca celdas Verde/Ámbar/Rojo.
- [ ] Vacaciones/Permiso Médico muestran iconos y períodos.
- [ ] Fotos ficticias visibles.
- [ ] Tarjeta persona muestra ID (no IP), turno y horas libres.
- [ ] Tarjeta persona-turno muestra IC explícito.

## E. Interacción tipo Excel
- [ ] Entrar a Seleccionar celdas.
- [ ] Seleccionar varias celdas.
- [ ] Eliminar varias asignaciones a la vez.
- [ ] Copiar selección.
- [ ] Cambiar a semana futura, seleccionar destino y Pegar.
- [ ] Destinos inválidos fallan individualmente sin revertir los válidos.

## F. Puestos / Turnos
- [ ] TIER visual y horas/semana requeridas.
- [ ] IC/ID promedio con semáforo.
- [ ] 1–2 turnos muestran Diurno/Nocturno.
- [ ] 3 turnos 06–14 / 14–22 / 22–06 muestran Mañana / Noche / Madrugada.

## G. Modales
- [ ] Click IC → 8 habilidades actual/requerido + cumplimiento general.
- [ ] Click Agente → ficha operacional ampliada.
- [ ] Click Puesto → cliente/punto/ciudad, Consignas, requisitos y Últimas novedades.
