# Pendientes (lista del 14/11/2026, revisada el 15/11)

## Hecho el 15/11
- Flecha `-|>`: no hacía falta cambiar nada. Las tres flechas `->` son asociaciones o agregaciones, no herencias.
- Diagrama de clases: corregidas las 5 firmas a las que les faltaba `:`. SVG regenerado.
- Secuencias: 112 mensajes con `param : Tipo` sacados del diagrama de clases, con las sobrecargas resueltas por los nombres de los parámetros. Se corrió `conectar_secuencia.py` (0 avisos) y se exportaron los 32 SVG. El constructor `Notificacion(...)` se partió en 2 líneas para que no se monte.
- Casos de uso:
  - el diagrama nuevo (la página 1 de `casos-de-uso.drawio.xml`, con 6 casos más) quedó como fuente única y se exportó a `Diagramas/UML/casos-de-uso.drawio.svg`, que reemplaza a `Untitled Diagram.drawio.svg`; el README ya apunta ahí;
  - corregidos «comunicado» e «Iniciar sesión».
- Código de los casos de uso del actor Sistema (`scheduler/`):
  - la liquidación mensual se genera sola el día 1;
  - el aviso de mora se envía con `PAGO_PENDIENTE` al marcar `EN_MORA`;
  - el recordatorio de reserva se envía cada hora, 24 h antes.
  - Hay 7 pruebas nuevas; en total pasan 213. README y ENDPOINTS.md están actualizados.

## Queda
- Revisar los diagramas y confirmar que están bien. Después, borrar los backups:
  - `Diagramas/Clases/DiagramaClasesSoftwareHorizontal.antes-de-firmas.xml`
  - `Diagramas/_fuente/secuencia-casos-de-uso.antes-de-tipos.xml`
  - `Diagramas/_fuente/secuencia-casos-de-uso.antes-de-conectar.xml`
  - `Diagramas/UML/casos-de-uso.antes-de-limpiar.drawio.xml` (el archivo de 23 páginas original)
- Commit, push y despliegue de los cambios del scheduler en la VM.
- Implementar la API de Gmail para enviar correos (hoy `CorreoLogSender` solo los escribe en el log). En el diagrama de despliegue tiene una nota «Pendiente de implementar».
