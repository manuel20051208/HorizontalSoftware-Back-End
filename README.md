# HorizontalSoftware — Back-End

API REST para un conjunto residencial en propiedad horizontal (Torres del Parque): cuotas de administración, pagos en línea con **Wompi**, reservas de zonas comunes, portería con invitados y notificaciones.

**Stack:** Java 25 · Spring Boot 4 · Spring Security (JWT) · PostgreSQL 16 + Flyway · Docker

El detalle de cada endpoint está en [`ENDPOINTS.md`](ENDPOINTS.md). Lo consume el front `software-residencial` (Next.js).

## Inicio rápido

```bash
cp .env.example .env     # completa las variables
./mvnw spring-boot:run   # levanta Postgres con docker compose; API en http://localhost:8080/api
./mvnw test              # pruebas
```

Al arrancar, Flyway crea las tablas y se crea un administrador semilla (`SEED_ADMIN_*`).

## Lo importante

- **Roles:** `ADMINISTRADOR`, `RESIDENTE` y `VIGILANTE`; los invitados no inician sesión. Cada endpoint declara su rol con `@PreAuthorize`.
- **Sesión:** access token de 15 min y refresh de 7 días que rota; reutilizar uno viejo anula la sesión.
- **Pagos:** el servidor fija el monto y solo aprueba con el webhook firmado de Wompi y una consulta directa que confirme estado, monto y moneda.
- **Tareas automáticas:** el día 1 de cada mes se genera la liquidación de todos los inmuebles activos; cada noche las liquidaciones vencidas pasan a `EN_MORA` y se avisa a sus residentes; cada hora se recuerdan las reservas que empiezan en 24 horas.

| Rol | Puede |
|---|---|
| Administrador | Inmuebles, usuarios, zonas comunes, liquidaciones, conciliar pagos y notificaciones |
| Residente | Estado de cuenta, pagar en línea, reservar zonas, registrar invitados y leer notificaciones |
| Vigilante | Verificar invitados y registrar su ingreso |

## Diagramas

### Casos de uso

![Diagrama de casos de uso](Diagramas/UML/casos-de-uso.drawio.svg)

### Clases

![Diagrama de clases](Diagramas/Clases/DiagramaClasesSoftwareHorizontal.drawio.svg)

### Componentes

![Diagrama de componentes](Diagramas/Componentes/componentes-backend.drawio.svg)

### Despliegue

Dónde está alojada la aplicación completa:
- el front (Next.js) en Vercel;
- la API en una VM de AWS EC2, dentro de Docker y detrás de nginx con HTTPS;
- la base de datos PostgreSQL en Neon;
- los pagos con Wompi;
- los correos con la Gmail API (OAuth2).

El navegador solo habla con Vercel y, al pagar, con el checkout de Wompi; es el servidor de Next quien llama a la API.

![Diagrama de despliegue](Diagramas/Despliegue/despliegue.drawio.svg)

### Paquetes

<details>
<summary><b>Para qué es cada paquete</b></summary>

| Paquete (`unisimon.demo`) | Para qué es |
|---|---|
| `controller` | Endpoints REST por rol y recurso |
| `scheduler` | Tareas programadas (liquidación mensual, mora y recordatorio de reservas) |
| `config` | Seguridad, CORS y cache |
| `security` | JWT, usuario autenticado y anotaciones de rol |
| `service` / `service/impl` / `service/support` | Casos de uso: leen con los repositorios, piden al modelo que decida, guardan y notifican (transacciones y cache) |
| `policy`, `factory` | Reglas de estado y acceso, creación de usuarios |
| `mapper` | Vistas SQL a objetos de respuesta |
| `cache` | Nombres de las caches |
| `integration/wompi` | Cliente de Wompi |
| `mail` | Correo: Gmail API si hay `GMAIL_REFRESH_TOKEN`, si no al log |
| `bootstrap` | Admin semilla y pasarela desde el `.env` |
| `repository` | SQL nativo con Spring Data |
| `model` | Dominio: las clases del diagrama de clases con sus reglas de negocio |
| `dto/request`, `dto/response` | Entrada y salida de la API |
| `exception` | Errores uniformes |

</details>

### Diagramas de secuencia

Un diagrama por caso de uso: los participantes son clases del diagrama de clases y los mensajes son sus operaciones, con los nombres y parámetros del diagrama. Entre todos usan cada operación del diagrama de clases. Las flechas están conectadas a sus barras de activación y líneas de vida, y los fragmentos `alt`/`opt`/`loop` van al frente. Fuente: [`Diagramas/_fuente/secuencia-casos-de-uso.xml`](Diagramas/_fuente/secuencia-casos-de-uso.xml); cómo editarlos y regenerarlos en «Editar los diagramas», más abajo.

<details>
<summary><b>Cuenta y sesión</b> (7)</summary>

**Iniciar sesión**

![Secuencia: Iniciar sesión](Diagramas/Secuencia/iniciar-sesion.svg)

**Recuperar contraseña**

![Secuencia: Recuperar contraseña](Diagramas/Secuencia/recuperar-contrasena.svg)

**Cambiar contraseña**

![Secuencia: Cambiar contraseña](Diagramas/Secuencia/cambiar-contrasena.svg)

**Gestionar cuentas de usuario**

![Secuencia: Gestionar cuentas de usuario](Diagramas/Secuencia/gestionar-cuentas-de-usuario.svg)

**Verificar correo** («extend» de *Gestionar cuentas de usuario*: cuando la cuenta nueva está pendiente, el administrador confirma el código de 4 cifras que llegó al correo y solo así se activa)

![Secuencia: Verificar correo](Diagramas/Secuencia/verificar-correo.svg)

**Relacionar con inmueble** («extend» de *Gestionar cuentas de usuario*: el administrador vincula, cambia o desvincula el inmueble de un residente; al desvincular una cuenta activa, la deja inactiva y sin sesión)

![Secuencia: Relacionar con inmueble](Diagramas/Secuencia/relacionar-con-inmueble.svg)

**Configurar preferencias de notificación**

![Secuencia: Configurar preferencias de notificación](Diagramas/Secuencia/configurar-preferencias-de-notificacion.svg)

</details>

<details>
<summary><b>Configuración del conjunto</b> (4)</summary>

**Configurar multas**

![Secuencia: Configurar multas](Diagramas/Secuencia/configurar-multas.svg)

**Configurar tarifas**

![Secuencia: Configurar tarifas](Diagramas/Secuencia/configurar-tarifas.svg)

**Configurar zonas comunes**

![Secuencia: Configurar zonas comunes](Diagramas/Secuencia/configurar-zonas-comunes.svg)

**Configurar reglas de reserva**

![Secuencia: Configurar reglas de reserva](Diagramas/Secuencia/configurar-reglas-de-reserva.svg)

</details>

<details>
<summary><b>Pagos y liquidaciones</b> (5)</summary>

**Consultar estado de cuenta e historial de pagos**

![Secuencia: Consultar estado de cuenta e historial de pagos](Diagramas/Secuencia/consultar-estado-de-cuenta-e-historial-de-pagos.svg)

**Realizar pagos**

![Secuencia: Realizar pagos](Diagramas/Secuencia/realizar-pagos.svg)

**Conciliar pagos de administración**

![Secuencia: Conciliar pagos de administración](Diagramas/Secuencia/conciliar-pagos-de-administracion.svg)

**Generar liquidación mensual**

![Secuencia: Generar liquidación mensual](Diagramas/Secuencia/generar-liquidacion-mensual.svg)

**Reembolsar pago**

![Secuencia: Reembolsar pago](Diagramas/Secuencia/reembolsar-pago.svg)

</details>

<details>
<summary><b>Comunicados y notificaciones</b> (7)</summary>

**Consultar comunicado oficial**

![Secuencia: Consultar comunicado oficial](Diagramas/Secuencia/consultar-comunicado-oficial.svg)

**Publicar comunicado oficial**

![Secuencia: Publicar comunicado oficial](Diagramas/Secuencia/publicar-comunicado-oficial.svg)

**Consultar confirmación de lectura**

![Secuencia: Consultar confirmación de lectura](Diagramas/Secuencia/consultar-confirmacion-de-lectura.svg)

**Consultar notificaciones**

![Secuencia: Consultar notificaciones](Diagramas/Secuencia/consultar-notificaciones.svg)

**Enviar notificación**

![Secuencia: Enviar notificación](Diagramas/Secuencia/enviar-notificacion.svg)

**Enviar notificaciones de vencimiento y mora**

![Secuencia: Enviar notificaciones de vencimiento y mora](Diagramas/Secuencia/enviar-notificaciones-de-vencimiento-y-mora.svg)

**Enviar recordatorio de reserva próxima**

![Secuencia: Enviar recordatorio de reserva próxima](Diagramas/Secuencia/enviar-recordatorio-de-reserva-proxima.svg)

</details>

<details>
<summary><b>Zonas comunes y reservas</b> (4)</summary>

**Consultar calendario de disponibilidad**

![Secuencia: Consultar calendario de disponibilidad](Diagramas/Secuencia/consultar-calendario-de-disponibilidad.svg)

**Reservar zona común**

![Secuencia: Reservar zona común](Diagramas/Secuencia/reservar-zona-comun.svg)

**Cancelar reserva**

![Secuencia: Cancelar reserva](Diagramas/Secuencia/cancelar-reserva.svg)

**Verificar acceso a zona común**

![Secuencia: Verificar acceso a zona común](Diagramas/Secuencia/verificar-acceso-a-zona-comun.svg)

</details>

<details>
<summary><b>Portería, incidencias y reportes</b> (6)</summary>

**Consultar dueño del carro**

![Secuencia: Consultar dueño del carro](Diagramas/Secuencia/consultar-dueno-del-carro.svg)

**Consultar relacionados al inmueble**

![Secuencia: Consultar relacionados al inmueble](Diagramas/Secuencia/consultar-relacionados-al-inmueble.svg)

**Consultar parqueaderos**

![Secuencia: Consultar parqueaderos](Diagramas/Secuencia/consultar-parqueaderos.svg)

**Registrar incidencia operativa**

![Secuencia: Registrar incidencia operativa](Diagramas/Secuencia/registrar-incidencia-operativa.svg)

**Consultar incidencias operativas**

![Secuencia: Consultar incidencias operativas](Diagramas/Secuencia/consultar-incidencias-operativas.svg)

**Generar reportes consolidados**

![Secuencia: Generar reportes consolidados](Diagramas/Secuencia/generar-reportes-consolidados.svg)

</details>

## Configuración (.env)

| Variable | Uso |
|---|---|
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` (`DB_URL`), `DB_POOL_MIN`, `DB_POOL_MAX` | PostgreSQL |
| `JWT_SECRET` | Firma de los JWT (≥ 32 caracteres) |
| `CORS_ALLOWED_ORIGINS` | Origen del front |
| `SEED_ENABLED`, `SEED_ADMIN_*` | Administrador inicial (`false` en producción) |
| `PASARELA_API_KEY`, `PASARELA_INTEGRITY_SECRET`, `PASARELA_EVENTS_SECRET`, `PASARELA_PRIVATE_KEY` | Llaves de Wompi |
| `PASARELA_URL_API_REST`, `PASARELA_REDIRECT_URL` | API de Wompi (sandbox o producción) y retorno al front |

Las llaves y la URL de Wompi deben ser del mismo ambiente; si no, el back no deja cobrar.

<details>
<summary>Todo en contenedores (API + Postgres)</summary>

```bash
./mvnw clean package
docker compose up --build
```

Usa `postgres:16-alpine`: la v18 cambió la ruta de datos y rompe el volumen.

</details>

<details>
<summary>Editar los diagramas</summary>

- **Secuencia:** la fuente es [`Diagramas/_fuente/secuencia-casos-de-uso.xml`](Diagramas/_fuente/secuencia-casos-de-uso.xml) (una página por caso de uso; se edita en draw.io). `python Diagramas/_fuente/exportar_secuencia_svg.py` exporta cada página a `Diagramas/Secuencia/<caso>.svg` con el visor oficial de draw.io (necesita internet); cada SVG se puede abrir y editar en draw.io. `python Diagramas/_fuente/conectar_secuencia.py` deja todas las flechas conectadas a sus barras y líneas de vida y trae los fragmentos `alt`/`opt`/`loop` al frente. `gen_secuencia_casos_uso.py --regenerar-xml` rehace el XML desde cero (se pierden los retoques hechos a mano).
- **Clases:** la fuente es [`Diagramas/Clases/DiagramaClasesSoftwareHorizontal.xml`](Diagramas/Clases/DiagramaClasesSoftwareHorizontal.xml). `python Diagramas/_fuente/gen_diagrama_clases.py` lo reorganiza en rejilla sin flechas cruzadas ni encima de las clases (mismo contenido), y `python Diagramas/_fuente/exportar_drawio_svg.py Diagramas/Clases/DiagramaClasesSoftwareHorizontal.xml Diagramas/Clases/DiagramaClasesSoftwareHorizontal.drawio.svg` regenera la imagen (editable en draw.io).
- **Despliegue:** `python Diagramas/_fuente/gen_despliegue.py` genera [`Diagramas/Despliegue/despliegue.xml`](Diagramas/Despliegue/despliegue.xml) y `python Diagramas/_fuente/exportar_drawio_svg.py Diagramas/Despliegue/despliegue.xml Diagramas/Despliegue/despliegue.drawio.svg` regenera la imagen.
- **Casos de uso y componentes:** abre el `.xml` / `.drawio.svg` en [draw.io](https://app.diagrams.net) → **Archivo → Exportar como → SVG**.
- **Fondo claro:** draw.io exporta SVG que se ven oscuros en modo oscuro. `python Diagramas/_fuente/fondo_claro.py` los deja siempre claros; el hook `scripts/hooks/pre-commit` lo corre solo al commitear (se activa una vez con `git config core.hooksPath scripts/hooks`).

</details>
