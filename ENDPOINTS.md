# ENDPOINTS - API de Torres del Parque

Contrato entre el backend (`HorizontalSoftware-Back-End`, Spring Boot) y el front-end (`software-residencial`, Next.js).

- **URL base:** `http://localhost:8080` (puerto por defecto de Spring). Todas las rutas empiezan por `/api`.
- **CORS:** el origen permitido es `http://localhost:3000` (`app.cors.allowed-origins`). Headers permitidos: `Authorization`, `Content-Type`, `X-Api-Key`.
- **Formato:** JSON. Fechas `yyyy-MM-dd`, horas `HH:mm` (o `HH:mm:ss`). Dinero como número decimal (COP).

## 1. Autenticación (OAuth2 + refresh token)

El backend es un servidor de recursos con JWT. Todo endpoint, salvo los marcados **Público**, exige el header:

```
Authorization: Bearer <access_token>
```

| Token | Duración | Uso |
|---|---|---|
| `access_token` | 15 min | Autentica cada petición |
| `refresh_token` | 7 días | Solo sirve para `POST /api/auth/refresh` |

**Rotación:** cada refresh devuelve un par nuevo e invalida el refresh anterior. Si se reutiliza un refresh ya rotado o revocado, la sesión se cierra por seguridad (hay que volver a hacer login).

**Flujo recomendado en el front:** guardar ambos tokens; ante un `401` hacer un único refresh y reintentar la petición; si el refresh falla, ir al login.

### Roles
`ADMINISTRADOR`, `RESIDENTE`, `VIGILANTE`. Cada endpoint declara su rol en la columna **Acceso**. Los invitados no inician sesión.

| Acceso | Significado |
|---|---|
| Público | Sin token |
| Autenticado | Cualquier usuario con sesión |
| ADMIN / RESIDENTE / VIGILANTE | Solo ese rol |
| ADMIN o RESIDENTE, ADMIN o VIGILANTE | Cualquiera de los dos |

## 2. Errores

Todos los errores tienen esta forma (`errores` solo aparece en validaciones):

```json
{
  "timestamp": "2026-11-08T15:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "mensaje": "Descripción legible para mostrar al usuario",
  "errores": { "campo": "mensaje de validación" }
}
```

| Status | Cuándo |
|---|---|
| 400 | Cuerpo inválido, parámetro faltante o regla de entrada no cumplida |
| 401 | Sin token, token vencido/inválido, credenciales o llave de pasarela incorrectas |
| 403 | El rol no tiene permiso, o el recurso es de otro inmueble/usuario |
| 404 | El recurso no existe |
| 409 | Conflicto de estado (duplicado, pago en estado no válido, zona sin cupo, etc.) |
| 500 | Error inesperado |

## 3. Auth - `/api/auth`

| Método | Ruta | Acceso | Cuerpo | Respuesta |
|---|---|---|---|---|
| POST | `/api/auth/login` | Público | `{correo, contrasena}` | 200 `Sesion` |
| POST | `/api/auth/refresh` | Público | `{refreshToken}` | 200 `Sesion` |
| POST | `/api/auth/logout` | Autenticado | - | 204 (revoca el refresh) |
| POST | `/api/auth/activar` | Público | `{correo, contrasena}` | 200 `{active, mensaje}` |
| POST | `/api/auth/recuperar-contrasena` | Público | `{correo}` | 202 `{mensaje}` (igual exista o no el correo) |
| PUT | `/api/auth/contrasena` | Autenticado | `{contrasenaActual, contrasenaNueva}` | 204 |
| GET | `/api/auth/me` | Autenticado | - | 200 `Usuario` |

**Sesion** (los tokens van en snake_case, estándar OAuth2):
```json
{
  "access_token": "...", "token_type": "Bearer", "expires_in": 900,
  "refresh_token": "...",
  "usuario": { "id": 1, "identificacion": 1000000001, "nombreCompleto": "...", "correoElectronico": "...",
               "tipoUsuario": "ADMINISTRADOR", "active": true, "notificacionLeida": false,
               "fechaCreacionCuenta": "2026-11-08", "inmuebleId": null, "direccionInmueble": null }
}
```
> Nota: `refreshToken` va en camelCase en el cuerpo de `/refresh`, pero llega como `refresh_token` en la respuesta.

**Contraseña nueva:** mínimo 8 caracteres, con al menos una letra y un número.

## 4. Administrador

### 4.1 Panel y liquidaciones - `/api/admin`
| Método | Ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|---|
| GET | `/api/admin/resumen?periodo=yyyy-MM-dd` | `periodo` opcional | 200 `ResumenAdmin` |
| POST | `/api/admin/liquidaciones` | `Generar` | 201 `Liquidacion` |
| POST | `/api/admin/liquidaciones/masiva` | `{periodo, fechaLimite?}` | 200 `{generadas, omitidas:[{inmuebleId, motivo}]}` |
| GET | `/api/admin/liquidaciones?inmuebleId=&estado=&periodo=` | filtros opcionales | 200 `Liquidacion[]` |
| PATCH | `/api/admin/liquidaciones/{id}/mora` | - | 200 `Liquidacion` |
| PATCH | `/api/admin/liquidaciones/{id}/al-dia` | - | 200 `Liquidacion` |

Todos: **ADMIN**.

- `Generar`: `{inmuebleId, periodo, fechaLimite?, cuotaAdministracion?, valorServiciosComunes?}`. El `periodo` se normaliza al día 1; `fechaLimite` por defecto es el día 10; las tarifas por defecto se copian de la liquidación anterior.
- `Liquidacion`: `{id, inmuebleId, direccionInmueble, periodo, fechaGeneracion, fechaLimite, cuotaAdministracion, valorServiciosComunes, valorMultas, total, estado, vencida}`. `estado`: `PENDIENTE`, `AL_DIA`, `EN_MORA`.
- `ResumenAdmin`: `{periodo, totalInmuebles, alDia, porVencer, vencidas, sinLiquidacion, porCobrar, recaudoMes}`.
- Una tarea programada pasa a `EN_MORA` las liquidaciones vencidas sin pagar todos los días a las 00:05 (America/Bogota).

### 4.2 Inmuebles - `/api/admin/inmuebles` (todos **ADMIN**)
| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/api/admin/inmuebles` | `{direccionInmueble, valorParqueadero?}` (formato `TORRE-APTO`, ej. `A-302`) | 201 `Detalle` |
| GET | `/api/admin/inmuebles` | - | 200 `Resumen[]` |
| GET | `/api/admin/inmuebles/{id}` | - | 200 `Detalle` |
| PUT | `/api/admin/inmuebles/{id}/tarifas` | `{cuotaAdministracion, valorServiciosComunes, valorParqueadero?}` | 200 `Liquidacion` |
| PUT | `/api/admin/inmuebles/{id}/multas` | `{valorMultas}` | 200 `Liquidacion` |
| PUT | `/api/admin/inmuebles/{id}/zonas-comunes` | `{zonaComunIds: [..]}` (no vacío) | 200 `Zona[]` |
| POST | `/api/admin/inmuebles/{id}/recordatorio-pago` | - | 201 `Notificacion` |
| POST | `/api/admin/inmuebles/{id}/conciliar-pago` | `{monto, metodoPago, referencia?}` | 201 `Pago` |

- `Resumen`: `{id, direccionInmueble, isActive, estadoPago, saldoPendiente, fechaLimitePendiente, valorParqueadero, totalLiquidaciones}`.
- `Detalle`: lo anterior más `nombresUsuarios`, `usuarios` (`Usuario[]`), `zonasComunes` (`Zona[]`) y `liquidacionVigente`.
- `estadoPago`: `AL_DIA`, `POR_VENCER`, `VENCIDA`, `SIN_LIQUIDACION`. `saldoPendiente` suma todas las liquidaciones que el inmueble no ha pagado.
- **Conciliar pago** (efectivo, transferencia...): el `monto` debe ser **igual** al saldo pendiente del inmueble; si no, 400. Queda `APROBADO` y deja al día el inmueble.

### 4.3 Usuarios - `/api/admin/usuarios` (todos **ADMIN**)
| Método | Ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|---|
| POST | `/api/admin/usuarios` | `CrearUsuario` | 201 `Usuario` |
| GET | `/api/admin/usuarios?tipo=&active=` | filtros opcionales | 200 `Usuario[]` |
| PATCH | `/api/admin/usuarios/{identificacion}/activo` | - | 200 `{id, nombreCompleto, active}` (alterna activo/inactivo) |

`CrearUsuario`: `{tipoUsuario, identificacion, nombreCompleto, fechaNacimiento?, correoElectronico, contrasena, inmuebleId?, inicioContrato?, venceContrato?}`.
`tipoUsuario`: `ADMINISTRADOR`, `RESIDENTE` o `VIGILANTE`. `inmuebleId` es obligatorio para `RESIDENTE`; `inicioContrato` y `venceContrato` para `ADMINISTRADOR` y `VIGILANTE`.

### 4.4 Zonas comunes - `/api/admin/zonas-comunes` (todos **ADMIN**)
| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/api/admin/zonas-comunes` | `{nombreZonaComun, capacidad, horaInicio, horaFin, aplicarATodosLosInmuebles?}` | 201 `Zona` |
| GET | `/api/admin/zonas-comunes` | - | 200 `Zona[]` |
| PATCH | `/api/admin/zonas-comunes/{id}/estado` | - | 200 `Zona` (alterna activa/inactiva) |

`Zona`: `{id, nombreZonaComun, capacidad, horaInicio, horaFin, isActive, reservasActuales}`. `aplicarATodosLosInmuebles` es `true` por defecto.

### 4.5 Reglas de reserva - `/api/admin` (todos **ADMIN**)
| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/api/admin/reglas-reserva` | `CrearRegla` | 201 `Regla` |
| GET | `/api/admin/reglas-reserva` | - | 200 `Regla[]` |
| PUT | `/api/admin/reglas-reserva/{id}` | `{anticipacionMinimaHoras, duracionMaximaHoras}` | 200 `Regla` |
| PATCH | `/api/admin/rompimientos/{id}/aplicar` | - | 200 `Rompimiento` |
| PATCH | `/api/admin/rompimientos/{id}/inactivar` | - | 200 `Rompimiento` |

- `CrearRegla`: `{anticipacionMinimaHoras, duracionMaximaHoras, horasAntelacionPenalidad?, tarifaPenalidad?, zonaComunIds: [..], observaciones?}`.
- `Regla`: `{id, anticipacionMinimaHoras, duracionMaximaHoras, horasAntelacionPenalidad, tarifaPenalidad, adminId, zonas: Rompimiento[]}`.
- `Rompimiento` (asignación de una regla a una zona): `{id, zonaComunId, nombreZonaComun, reglaReservaId, fechaAplicacion, activa, observaciones}`.

### 4.6 Notificaciones - `/api/admin/notificaciones` (todos **ADMIN**)
| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/api/admin/notificaciones` | `{mensaje, tipoNotificacion?, usuarioIds?, inmuebleId?}` | 201 `Notificacion` |
| GET | `/api/admin/notificaciones/{id}/lectura` | - | 200 `{notificacionId, leidos:[..], pendientes:[..]}` |

Destinatarios: `usuarioIds`, o `inmuebleId` (sus residentes activos). Si no se envía ninguno, la reciben todos los residentes activos. `tipoNotificacion` por defecto `GENERAL`. `leidos`/`pendientes` son `{usuarioId, nombreCompleto}`.

### 4.7 Pagos y pasarela - `/api/admin` (todos **ADMIN**)
| Método | Ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|---|
| GET | `/api/admin/pagos?inmuebleId=&estado=` | filtros opcionales (máx. 200 resultados, más recientes primero) | 200 `Pago[]` |
| POST | `/api/admin/pagos/{id}/anular` | - | 200 `Pago` (solo `PENDIENTE`) |
| POST | `/api/admin/pagos/{id}/reembolso` | - | 200 `Pago` (solo `APROBADO`) |
| GET | `/api/admin/pasarela` | - | 200 `Pasarela` |
| PUT | `/api/admin/pasarela` | `{nombrePasarela, urlApi, apiKey, estado}` | 200 `Pasarela` |

- `Pago`: `{id, inmuebleId, direccionInmueble, monto, fechaPago, concepto, metodoPago, estado, referencia}`.
- `estado` del pago: `PENDIENTE`, `APROBADO`, `RECHAZADO`, `ANULADO`, `REEMBOLSADO`.
- `Pasarela`: `{id, nombrePasarela, urlApi, apiKey, estado}`. **`apiKey` sale enmascarada** (solo los últimos 4 caracteres). `estado`: `ACTIVA` o `INACTIVA`.
- Anular un pago que no está `PENDIENTE` o reembolsar uno que no está `APROBADO` devuelve 409. Reembolsar reabre la liquidación vigente y notifica al inmueble.

## 5. Residente - `/api/residente` (todos **RESIDENTE**)

### 5.1 Cuenta y pagos
| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/residente/mi-cuenta` | - | 200 `EstadoCuenta` |
| GET | `/api/residente/pagos` | - | 200 `Pago[]` (historial del inmueble) |
| POST | `/api/residente/pagos` | `{metodoPago}` | 201 `{pago, urlCheckout}` |
| GET | `/api/residente/pagos/{id}` | - | 200 `Pago` (403 si es de otro inmueble) |

- `EstadoCuenta`: `{inmuebleId, direccionInmueble, estadoPago, saldoPendiente, pazYSalvo, liquidacionVigente}`.
- **Pagar:** `metodoPago` es `PSE`, `TARJETA` o `NEQUI`. **El valor lo calcula el servidor** (saldo pendiente); el cliente nunca envía el monto. El pago nace `PENDIENTE` y el front debe **redirigir al residente a `urlCheckout`**. El resultado llega después por webhook (ver sección 8); el front puede consultarlo con `GET /api/pagos/estado/{referencia}`.
- Errores típicos: 403 cuenta inactiva; 409 sin saldo pendiente o pasarela no configurada.

### 5.2 Invitados y auto
| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/residente/invitados` | - | 200 `Invitado[]` |
| POST | `/api/residente/invitados` | `CrearInvitado` | 201 `Invitado` |
| PATCH | `/api/residente/invitados/{id}/estado` | - | 200 `{invitadoId, active}` (alterna) |
| PATCH | `/api/residente/invitados/estado` | `{invitadoIds: [..]}` | 200 `{cambios:[{invitadoId, active}]}` |
| GET | `/api/residente/auto` | - | 200 `Auto` |
| POST | `/api/residente/auto` | `{placa, parqueaderoId?}` | 201 `Auto` |

- `CrearInvitado`: `{identificacion, nombreCompleto, fechaNacimiento?, correoElectronico?, venceAutorizacion?}`. Sin `venceAutorizacion` la autorización es permanente.
- `Invitado`: `{id, identificacion, nombreCompleto, correoElectronico, active, venceAutorizacion, vigente}`. `vigente` = activo y autorización no vencida.
- `Auto`: `{id, duenoId, nombreDueno, placa, parqueaderoId, inmuebleId, direccionInmueble}`. La `placa` es numérica (así está en el diagrama de clases).

### 5.3 Zonas comunes y reservas
| Método | Ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|---|
| GET | `/api/residente/zonas-comunes` | - | 200 `Zona[]` (las activas asignadas a su inmueble) |
| GET | `/api/residente/zonas-comunes/{id}/disponibilidad?hora=HH:mm` | `hora` obligatoria | 200 `Disponibilidad` |
| GET | `/api/residente/reservas` | - | 200 `Reserva[]` (las suyas) |
| POST | `/api/residente/reservas` | `{zonaComunId, hora}` | 201 `Reserva` |
| DELETE | `/api/residente/reservas/{id}` | - | 204 (solo quien reservó) |

- `Disponibilidad`: `{zonaComunId, hora, horarioValido, disponible, habilitado, mensaje}`.
- `Reserva`: `{id, zonaComunId, nombreZonaComun, horaInicio, horaFin, enCurso, duenoReservaId, nombreDueno, inmuebleId, direccionInmueble}`. `enCurso`: la zona está abierta ahora y activa.
- **Reglas al reservar:** la zona debe estar asignada y activa para su inmueble (403 si no); el residente debe estar **habilitado** (cuenta activa, inmueble activo y sin mora; 403 si no); la `hora` debe caer dentro del horario de la zona (409); la zona debe tener cupo (409); no se reserva dos veces la misma zona (409).
- El modelo de reserva (según el diagrama de clases) no guarda fecha: el cupo cuenta las reservas existentes de la zona.

## 6. Notificaciones - `/api/notificaciones` (**Autenticado**)
| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/notificaciones` | 200 `Notificacion[]` (las del usuario) |
| PUT | `/api/notificaciones/leida` | 204 (marca todas como leídas) |

`Notificacion`: `{id, mensaje, tipoNotificacion, fechaEnvio}` (`fechaEnvio` en formato `yyyy-MM-ddTHH:mm:ss`).

## 7. Pagos compartidos y consultas

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/api/pagos/{id}/comprobante` | ADMIN o RESIDENTE | 200 `{referencia, comprobante}` (texto; solo pagos `APROBADO`/`REEMBOLSADO`, 409 si no) |
| GET | `/api/pagos/estado/{referencia}` | ADMIN o RESIDENTE | 200 `{referencia, estado}` |
| GET | `/api/invitados/{id}/residentes` | ADMIN o VIGILANTE | 200 `Anfitrion[]` |
| GET | `/api/autos/{id}/duenos?inmuebleId=` | ADMIN o VIGILANTE | 200 `{autoId, inmuebleId, duenos:[..]}` |

Un residente solo accede a pagos de su propio inmueble (403 si no).
`Anfitrion` / `Dueno`: `{id, identificacion, nombreCompleto, inmuebleId, direccionInmueble}`.

## 8. Webhook de la pasarela (Wompi)

| Método | Ruta | Acceso | Cuerpo | Respuesta |
|---|---|---|---|---|
| POST | `/api/pasarela/webhook` | Público + header `X-Api-Key` | `{referencia, estado}` (`APROBADO` o `RECHAZADO`) | 200 `Pago` |

Lo llama la pasarela, **no el front-end**. No usa Bearer: se autentica con la llave de la pasarela en `X-Api-Key` (401 si falta o no coincide). Es idempotente: un aviso repetido no cambia nada ni vuelve a notificar. Al aprobar, la liquidación del inmueble queda `AL_DIA` y se notifica el resultado.

## 9. Vigilante - `/api/vigilante` (todos **VIGILANTE**)
| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/vigilante/reservas` | 200 `Reserva[]` (todas) |
| GET | `/api/vigilante/zonas-comunes/acceso?identificacion=` | 200 `Acceso` |
| GET | `/api/vigilante/invitados/{identificacion}` | 200 `InvitadoConsulta` |
| POST | `/api/vigilante/invitados/{identificacion}/ingreso` | 200 `Ingreso` |

- `Acceso`: `{identificacion, nombreCompleto, permitido, motivo, zonasPermitidas:[{zonaComunId, nombreZonaComun, horaInicio, horaFin}]}`. `permitido`: la persona puede entrar ahora a alguna zona (la reservó ella o su anfitrión).
- `InvitadoConsulta`: `{existe, vigente, invitado}`.
- `Ingreso`: `{permitido, mensaje, visitante, inmueblesNotificados:[..]}`. Registrar el ingreso notifica a los inmuebles anfitriones.

## 10. Resumen de acceso por rol

| Rol | Rutas |
|---|---|
| Público | `/api/auth/login`, `/refresh`, `/activar`, `/recuperar-contrasena`, `/api/pasarela/webhook` (con `X-Api-Key`) |
| Autenticado | `/api/auth/logout`, `/contrasena`, `/me`, `/api/notificaciones/**` |
| ADMINISTRADOR | `/api/admin/**` |
| RESIDENTE | `/api/residente/**` |
| VIGILANTE | `/api/vigilante/**` |
| ADMIN o RESIDENTE | `/api/pagos/**` |
| ADMIN o VIGILANTE | `/api/invitados/{id}/residentes`, `/api/autos/{id}/duenos` |

La protección está en anotaciones sobre cada método, no en reglas por URL. Una prueba garantiza que ningún endpoint quede sin etiqueta.
