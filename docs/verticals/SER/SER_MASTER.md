# SER — Servicios v0.6

## Propósito
Superficie operacional de SGI: Comando sobre Servicio / Punto / Puesto recibidos desde SIC: COM, separando Configuración de Operación.

## Listado maestro
Unidad de fila: **Cliente · Punto**.
Acciones: **Operación** y **Configuración**.

## Configuración del Punto
Landing de configuración con módulos: ATS, Puestos, Bitácora, Patrullas, Consignas, Recursos Humanos, Recursos Materiales e Historial.

## ATS
SGI admite carga manual de archivos `.ats` publicados por ATS. El paquete se interpreta como ZIP estructurado y se conserva íntegro. SGI materializa el plano principal declarado en `model/pto.json` para visualización y vínculos operacionales.

Persistencia: `ats_point_package`, con historial por revisión y un único `is_current=true` por Punto.

## Puestos
SIC: COM mantiene ownership de los datos contractuales. SGI configura Tipo de Puesto, Descripción, ubicación sobre plano ATS y requerimientos de habilidades.

La ubicación es geométrica: coordenadas normalizadas X/Y vinculadas al `ats_package_id` vigente.

## Habilidades
8 habilidades, rango requerido 1–5, máximo una en 5, máximo dos en 4, suma total máxima 22.

## Operación
La visualización operativa del Punto permanece fuera del alcance de v0.5.


## Bitácora
Configuración por Puesto mediante Protocolos. Cada Protocolo define PAX/VHL/CONT, Ingreso/Egreso/Ambos, reglas de Identificación, Verificación, Autorización, Evidencias, Captura, Listas y Trazabilidad.

Los campos soportan evidencia y Foto estándar real para preparar la futura comparación automática con VISINT. La captura ejecutada por agentes continúa fuera del alcance de Configuración.
