# HorizontalSoftware — Back-End

API REST para administrar un conjunto residencial en **propiedad horizontal** (Torres del Parque): cuotas de administración, pagos en línea con **Wompi**, reservas de zonas comunes, portería con invitados y notificaciones.

> **En una frase:** el administrador genera la cuota de cada inmueble, el residente la paga en línea y reserva zonas comunes, y el vigilante controla quién entra.

| | |
|---|---|
| **Stack** | Java 25 · Spring Boot 4 · Spring Security (OAuth2 Resource Server + JWT) · PostgreSQL 16 · Flyway |
| **Front que lo consume** | `software-residencial` (Next.js) |
| **Contrato de la API** | [`ENDPOINTS.md`](ENDPOINTS.md) |
| **Diagramas** | carpeta [`Diagramas/`](Diagramas) |

---

## Contenido

1. [Lo más importante](#1-lo-más-importante)
2. [Casos de uso (UML)](#2-casos-de-uso-uml)
3. [Modelo de clases (UML)](#3-modelo-de-clases-uml)
4. [Diagramas de secuencia](#4-diagramas-de-secuencia)
5. [Arquitectura y paquetes](#5-arquitectura-y-paquetes)
6. [Cómo correrlo](#6-cómo-correrlo)
7. [Configuración (.env)](#7-configuración-env)

---

## 1. Lo más importante

- **Tres roles con sesión:** `ADMINISTRADOR`, `RESIDENTE`, `VIGILANTE`. Los invitados existen como usuarios, pero **no inician sesión**. Cada endpoint declara su rol con `@PreAuthorize`.
- **Sesión con JWT:** `access_token` de 15 min y `refresh_token` de 7 días que **rota** en cada renovación. Si alguien reutiliza un refresh viejo, la sesión se anula.
- **El dinero lo decide el servidor:** el residente no envía el monto. El back calcula el saldo, firma el checkout de Wompi y solo aprueba el pago cuando el **webhook firmado** llega y la consulta directa a Wompi confirma estado, monto y moneda.
- **El `.env` es la única fuente de verdad** para la pasarela: al arrancar se valida, y si algo está mal el back no deja cobrar.
- **Base de datos versionada con Flyway** (`src/main/resources/db/migration`) y consultas en **SQL nativo**.
- **Una tarea nocturna** (`LiquidacionScheduler`) pasa a `EN_MORA` las liquidaciones vencidas.

### Qué puede hacer cada rol

| Rol | Puede |
|---|---|
| **Administrador** | Gestionar inmuebles, usuarios, zonas comunes y reglas de reserva · generar liquidaciones mensuales · conciliar pagos · enviar notificaciones |
| **Residente** | Ver su estado de cuenta · pagar en línea · reservar zonas comunes · registrar invitados y su auto · leer notificaciones |
| **Vigilante** | Verificar invitados y registrar su ingreso (el residente recibe el aviso) |

---

## 2. Casos de uso (UML)

Quién hace qué en el sistema, agrupado por módulo: actores y configuración base, pagos, zonas comunes, portería, comunicación, notificaciones y reportes.

![Diagrama de casos de uso](Diagramas/UML/Untitled%20Diagram.drawio.svg)

> Fuente editable en draw.io: [`Diagramas/UML/Untitled Diagram.drawio.svg`](Diagramas/UML/Untitled%20Diagram.drawio.svg)

---

## 3. Modelo de clases (UML)

Vista resumida del dominio (`src/main/java/unisimon/demo/model`). La idea central: **todo gira alrededor del `Inmueble`**; los usuarios heredan de `Usuario`, y el invitado (`UsuarioAutorizado`) es un tipo de residente sin sesión.

```mermaid
classDiagram
    direction LR

    class Usuario {
        identificacion
        nombre
        correo
        rol
    }
    class NoAdmin {
        <<interface>>
    }
    Usuario <|-- Administrador
    Usuario <|-- Residente
    Usuario <|-- Vigilante
    Residente <|-- UsuarioAutorizado : invitado
    NoAdmin <|.. Residente
    NoAdmin <|.. Vigilante

    Inmueble "1" o-- "*" Residente : habitan
    Inmueble "1" -- "0..1" Parqueadero
    Inmueble "1" -- "*" Liquidacion : cuotas
    Inmueble "1" -- "*" Pago
    Inmueble "*" -- "*" ZonaComun : acceso
    Inmueble "*" -- "*" Notificacion

    Residente "1" -- "*" UsuarioAutorizado : invita
    Residente "1" -- "0..1" Auto
    Auto "0..1" -- "0..1" Parqueadero

    Pago "*" --> "1" PasarelaPago : Wompi
    ReservaZonaComun "*" --> "1" Residente : dueño
    ReservaZonaComun "*" --> "1" ZonaComun
    ZonaComun "1" -- "*" Rompimiento
    Rompimiento "*" --> "1" ReglaReserva
    Notificacion "*" -- "*" Usuario : destinatarios
```

> Diagrama completo con atributos y métodos (draw.io): [`Diagramas/Clases/DiagramaClasesSoftwareHorizontal.xml`](Diagramas/Clases/DiagramaClasesSoftwareHorizontal.xml) — ábrelo en [app.diagrams.net](https://app.diagrams.net).

---

## 4. Diagramas de secuencia

Los flujos clave, de petición a base de datos. Cada uno tiene además su versión interactiva en [`Diagramas/Secuencia/`](Diagramas/Secuencia) (abre el `.html` en el navegador).

### 4.1 Sesión con refresh token rotativo

Login, y cómo se renueva la sesión sin pedir la contraseña otra vez.

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant A as AuthController
    participant S as AutenticacionService
    participant T as JwtTokenService
    participant DB as PostgreSQL

    C->>A: POST /api/auth/login
    A->>S: iniciarSesion
    S->>DB: validar credenciales
    S->>T: emitir access + refresh
    Note over S,T: Credenciales malas → 401<br/>Invitado o cuenta inactiva → 403
    S->>DB: guardar huella del refresh
    A-->>C: access (15 min) + refresh (7 días)

    C->>A: POST /api/auth/refresh
    A->>S: renovarSesion
    S->>T: leer refresh
    S->>DB: comparar huella
    Note over S,DB: Si no coincide se anula la sesión → 401
    S->>T: emitir nuevo par
    A-->>C: tokens rotados
```

Interactivo: [`sesion-jwt.html`](Diagramas/Secuencia/sesion-jwt.html)

### 4.2 Pago de administración con Wompi

El flujo más delicado del sistema: **el front nunca decide si un pago quedó aprobado**, lo decide el back con el webhook firmado y confirmando con Wompi.

```mermaid
sequenceDiagram
    autonumber
    actor R as Residente
    participant F as Front
    participant API as API de pagos
    participant DB as PostgreSQL
    participant W as Wompi

    R->>F: pagar saldo
    F->>API: POST /api/residente/pagos (@SoloResidente)
    Note over API: El monto lo fija el servidor<br/>y valida que la pasarela esté lista
    API->>DB: INSERT pago PENDIENTE
    API-->>F: urlCheckout firmada
    Note over API,F: firma = SHA-256(ref + centavos + COP + secreto)
    F->>W: abre checkout
    R->>W: paga
    W->>API: POST /api/pasarela/webhook (público)
    Note over API: Verifica el checksum con el secreto de eventos<br/>Firma inválida → 401
    API->>W: GET /transactions/{id} (llave privada)
    W-->>API: estado, monto, moneda
    API->>DB: UPDATE a APROBADO o RECHAZADO
    Note over API,DB: Solo si sigue PENDIENTE y monto y moneda coinciden (idempotente)
    API-->>W: 200 OK
    W-->>F: redirect
    F->>API: GET /api/pagos/estado/{ref}
    API-->>F: estado del pago
    F-->>R: muestra resultado
```

Interactivo: [`pago-wompi-v1.html`](Diagramas/Secuencia/pago-wompi-v1.html) · [`pagos-wompi.html`](Diagramas/Secuencia/pagos-wompi.html)

### 4.3 Liquidaciones de administración

Cómo nace la cuota mensual de un inmueble y cuándo pasa a mora.

```mermaid
sequenceDiagram
    autonumber
    actor Ad as Administrador
    participant C as AdminLiquidacionController
    participant S as LiquidacionService
    participant DB as PostgreSQL
    participant J as Tarea nocturna

    Ad->>C: POST /api/admin/liquidaciones
    C->>S: generarLiquidacion
    S->>DB: inmueble y periodo
    Note over S,DB: Inmueble inactivo o periodo repetido → 409
    DB-->>S: liquidación previa
    S->>DB: insertar liquidación
    Note over S,DB: Sin cuota indicada copia la previa<br/>Sin previa → 400
    S-->>C: liquidación

    Ad->>C: PATCH /api/admin/liquidaciones/{id}/mora
    C->>S: marcarEnMora
    S->>DB: estado EN_MORA

    J-)S: marcarVencidas
    S-)DB: PENDIENTE vencidas → EN_MORA
```

Interactivo: [`liquidaciones.html`](Diagramas/Secuencia/liquidaciones.html)

### 4.4 Reservar una zona común

```mermaid
sequenceDiagram
    autonumber
    actor R as Residente
    participant C as ResidenteReservaController
    participant S as ReservaService
    participant Repo as Repositorios
    participant N as Notificaciones

    R->>C: POST /api/residente/reservas
    C->>S: reservarZonaComun
    S->>Repo: contexto, zona y reglas
    Repo-->>S: datos
    Note over S: Valida fecha, zona habilitada, horario,<br/>reglas, cupo y que no se solape
    S->>Repo: insertar reserva
    Repo-->>S: id
    S-)N: notificarReserva
    N-)Repo: guardar aviso
    S-->>C: reserva
    C-->>R: 200 reserva
```

Interactivo: [`reservas.html`](Diagramas/Secuencia/reservas.html)

### 4.5 Ingreso de un invitado por portería

```mermaid
sequenceDiagram
    autonumber
    actor V as Vigilante
    participant C as VigilanteController
    participant S as VigilanteService
    participant DB as PostgreSQL
    participant N as Notificaciones

    V->>C: GET /api/vigilante/invitados/{identificacion}
    C->>S: existeInvitado
    S->>DB: buscar invitado
    S-->>V: existe y vigente
    V->>C: POST /invitados/{identificacion}/ingreso
    C->>S: registrarIngreso
    S->>DB: invitado y anfitriones
    Note over S,DB: Inactivo o vencido → se deniega sin avisar
    S-)N: notificarVisita
    N-)DB: aviso a los residentes
    S-->>V: ingreso permitido
```

Interactivo: [`porteria-invitados.html`](Diagramas/Secuencia/porteria-invitados.html)

### Otros flujos

| Flujo | Qué muestra | Diagrama |
|---|---|---|
| Zonas comunes (admin) | Crear, listar con caché y activar/inactivar zonas | [`zonas-comunes.html`](Diagramas/Secuencia/zonas-comunes.html) |
| Notificaciones | El admin envía, el residente lista y marca como leída | [`notificaciones.html`](Diagramas/Secuencia/notificaciones.html) |

---

## 5. Arquitectura y paquetes

Arquitectura por capas clásica: **Controller → Service → Repository → PostgreSQL**, con la seguridad delante de todo.

```mermaid
flowchart LR
    Front[Front Next.js] -->|HTTPS + Bearer JWT| Sec[Spring Security<br/>JWT + @PreAuthorize]
    Wompi[Wompi] -->|webhook firmado| Sec
    Sec --> Ctrl[controller]
    Ctrl --> Srv[service]
    Srv --> Repo[repository<br/>SQL nativo]
    Repo --> DB[(PostgreSQL<br/>Flyway)]
    Srv --> Int[integration/wompi]
    Int -->|API REST| Wompi
    Srv --> Mail[mail]
    Sched[scheduler<br/>tarea nocturna] --> Srv
    Srv --> Cache[cache]
```

| Paquete (`unisimon.demo`) | Para qué es |
|---|---|
| `controller` | Endpoints REST, uno por rol/recurso (`Admin*`, `Residente*`, `Vigilante*`, `Auth`, `PasarelaWebhook`) |
| `service` / `service/impl` | Reglas de negocio (interfaces + implementaciones) |
| `repository` | Acceso a datos con SQL nativo |
| `model` | Clases del dominio |
| `dto/request`, `dto/response`, `mapper` | Lo que entra y sale de la API, y la conversión desde el modelo |
| `security` | Emisión/lectura de JWT, roles y anotaciones de acceso |
| `policy`, `factory` | Reglas reutilizables y creación de objetos |
| `integration/wompi` | Cliente de la pasarela de pagos |
| `scheduler` | Tareas programadas (mora de liquidaciones) |
| `exception` | Manejo uniforme de errores (404, 405, 409…) |
| `bootstrap` | Datos iniciales (admin semilla, sincronizar pasarela con el `.env`) |

Diagramas completos: [`Arquitectura/backend-horizontal.html`](Diagramas/Arquitectura/backend-horizontal.html) · [`Arquitectura/backend-v1.html`](Diagramas/Arquitectura/backend-v1.html) · [`Paquetes/paquetes-backend.xml`](Diagramas/Paquetes/paquetes-backend.xml) (draw.io)

---

## 6. Cómo correrlo

**Requisitos:** JDK 25 y Docker.

```bash
# 1. Configura las variables
cp .env.example .env

# 2. Arranca (Spring levanta Postgres solo con docker compose)
./mvnw spring-boot:run
```

La API queda en `http://localhost:8080` y todas las rutas empiezan por `/api`. Al arrancar, Flyway crea las tablas y se crea un administrador semilla (`SEED_ADMIN_*` en el `.env`).

**Pruebas:**

```bash
./mvnw test
```

**Todo en contenedores (API + Postgres):**

```bash
./mvnw clean package
docker compose up --build
```

> Usa `postgres:16-alpine`, no `postgres:latest`: la v18 cambió la ruta de datos y rompe el volumen.

---

## 7. Configuración (.env)

Copia [`.env.example`](.env.example) a `.env`. El `.env` **no se sube a git**.

| Variable | Para qué |
|---|---|
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` (`DB_URL` opcional) | Conexión a PostgreSQL |
| `DB_POOL_MIN`, `DB_POOL_MAX` | Tamaño del pool de conexiones |
| `JWT_SECRET` | Llave que firma los JWT (mínimo 32 caracteres) |
| `CORS_ALLOWED_ORIGINS` | Origen del front (por defecto `http://localhost:3000`) |
| `SEED_ENABLED`, `SEED_ADMIN_*` | Administrador inicial (`false` en producción) |
| `PASARELA_API_KEY` | Llave pública de Wompi: abre el checkout |
| `PASARELA_INTEGRITY_SECRET` | Firma el checkout |
| `PASARELA_EVENTS_SECRET` | Verifica la firma del webhook |
| `PASARELA_PRIVATE_KEY` | Consulta la transacción en Wompi antes de aprobar |
| `PASARELA_URL_API_REST` | `https://sandbox.wompi.co/v1` (pruebas) o `https://production.wompi.co/v1` |
| `PASARELA_REDIRECT_URL` | Pantalla del front a la que vuelve el residente (opcional) |

> Las cuatro llaves de Wompi y la URL REST deben ser **del mismo ambiente** (pruebas o producción). Si no, el back lo detecta al arrancar y no deja cobrar.

---

Para el detalle de cada endpoint (rutas, cuerpos, respuestas y errores) ve a **[`ENDPOINTS.md`](ENDPOINTS.md)**.
