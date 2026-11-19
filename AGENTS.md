# Reglas para agentes de código (opencode) en HorizontalSoftware-Back-End

Backend Spring Boot (Java, paquete `unisimon.demo`) de una propiedad horizontal. Base de datos PostgreSQL (Neon en
producción). Front-end hermano en `../HorizontalSoftware-Front-End` (Next.js).

## Cómo trabajamos

- **Claude planea, tú codificas.** Claude (Claude Code) te pasa un plan con los archivos y el comportamiento
  esperado. Síguelo. Si algo del plan choca con estas reglas o con el código real, **detente y explícalo** en tu
  respuesta en vez de improvisar otra solución.
- **No hagas commit, push ni despliegues.** Claude revisa el diff, corre las pruebas y commitea a nombre de Manuel.
- **No toques** `Diagramas/` (los diagramas UML los mantiene Claude), `ENDPOINTS.md` de este repo (está congelado;
  el contrato vivo es `../HorizontalSoftware-Front-End/ENDPOINTS.md`) ni el repo del front, salvo que el plan lo pida.
- **Nunca** leas, copies ni respaldes archivos `.env`, llaves `.pem` o secretos. No los imprimas.
- Al terminar responde con: archivos creados/cambiados, qué hace cada cambio y el resultado real de las pruebas
  (número de tests, fallos). Nunca digas que pasan si no las corriste.

## Arquitectura (SOLID estricto)

- Capas separadas: `controller` → `service` (interfaz) + `service/impl` → `repository`; además `model`, `dto/request`,
  `dto/response`, `mapper`, `policy`, `factory`, `security`, `config`, `exception`, `mail`, `integration`,
  `scheduler`, `bootstrap`, `cache`. Cero lógica de negocio en controladores.
- **El modelo (`src/main/java/unisimon/demo/model/*.java`) es INTOCABLE.** No lo edites. Si el diagrama y el
  código no cuadran, la solución va en servicios, configuración o una migración nueva. Las operaciones del modelo
  deciden con objetos ya cargados; el servicio lee de la base, arma el objeto (`mapper/Hidratador`), llama a la
  operación del modelo y guarda.
- Inyección por constructor con `@RequiredArgsConstructor` y campos `private final`. Nada de `new` de dependencias.
- El tiempo siempre con el `Clock` inyectado (`LocalDateTime.now(reloj)`), nunca `now()` sin reloj.
- Salidas externas detrás de un puerto (interfaz): `CorreoSender`, `ClienteWompi`, `TokenService`.

## Base de datos

- **Flyway** en `src/main/resources/db/migration`. La última aplicada es **V9**; la siguiente es `V10__<descripcion>.sql`.
  **Nunca** edites ni borres una migración existente (V7 y V8 incluidas): un cambio se deshace con otra migración.
  `ddl-auto=none`: Hibernate no crea ni valida esquema.
- **Solo SQL nativo de PostgreSQL** en los repositorios (`@Query(nativeQuery = true)`), sobre `Repository<T, ID>`.
  Lecturas con **proyecciones** (interfaces `...View` con alias entre comillas: `AS "nombreCampo"`).
- Escrituras **por columna** (`@Modifying` + `UPDATE ... SET col = :valor`), nunca cargar y guardar la entidad entera.
- Imágenes de Postgres: `postgres:16-alpine`, nunca `latest` (PG18 rompe el montaje).

## Seguridad y API

- OAuth2 resource server con JWT (access + refresh con rotación). No cambies ese esquema.
- Protección **por anotación en cada método del controlador**: `@Publico`, `@Autenticado`, `@SoloAdministrador`,
  `@SoloResidente`, `@SoloVigilante`, `@AdministradorOResidente`, `@AdministradorOVigilante`
  (`security/annotation`). Nunca reglas por URL. Una prueba falla si un endpoint queda sin etiqueta.
- El usuario autenticado llega como parámetro `UsuarioAutenticado usuario`.
- Cuerpos de entrada como `record` en `dto/request/*Requests.java` con Bean Validation (`@Valid`). Respuestas como
  `record` en `dto/response/*Responses.java`, armadas en `mapper/*Mapper`.
- Errores: lanza las subclases de `ApiException` (`SolicitudInvalidaException` 400, `NoAutenticadoException` 401,
  `ProhibidoException` 403, `RecursoNoEncontradoException` 404, `ConflictoException` 409). Solo
  `GlobalExceptionHandler` las convierte a HTTP. Mensajes en español **sin tildes** dentro del código Java.
- Transacciones: `@Transactional` en los `service/impl`. Una propagación distinta (`REQUIRES_NEW`, etc.) solo
  funciona llamando a **otro bean** (pasa por el proxy), nunca con `this.metodo()`.
- Cachés: nombres solo desde `cache/NombresCache`.

## Estilo

- Comentarios y Javadoc en español, cortos, explicando el porqué. Nombres en español como el resto del código.
- Sigue el estilo de los archivos vecinos (mismo orden de imports, misma forma de los records y queries).

## Pruebas (obligatorio)

- JUnit 5 + Mockito, **sin base de datos ni Docker**. Para proyecciones usa `support/Proyecciones.de(...)`.
- Todo cambio de comportamiento lleva su prueba (caso feliz y rechazos).
- Antes de responder corre `./mvnw -o test` (en Windows `mvnw.cmd -o test`) y deja **todo en verde**. Si algo
  falla, arréglalo; si no puedes, dilo con la salida del error.
