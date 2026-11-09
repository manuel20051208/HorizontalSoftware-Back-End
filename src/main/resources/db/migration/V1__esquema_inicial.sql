-- V1: esquema inicial (PostgreSQL).
-- Lo gestiona Flyway. Hibernate NO crea ni modifica nada (spring.jpa.hibernate.ddl-auto=none).
-- Una sola tabla "usuarios" para la herencia Usuario > Administrador / Residente / Vigilante / UsuarioAutorizado
-- (columna discriminadora: tipo_usuario).

CREATE TABLE inmuebles (
    id                    BIGSERIAL    PRIMARY KEY,
    direccion_inmueble    VARCHAR(100) NOT NULL UNIQUE,   -- formato TORRE-APTO, ej. A-302
    liquidacion_actual_id BIGINT,                         -- FK a liquidaciones (se agrega abajo)
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE usuarios (
    id                        BIGSERIAL    PRIMARY KEY,
    tipo_usuario              VARCHAR(30)  NOT NULL,
    identificacion            BIGINT       NOT NULL UNIQUE,
    nombre_completo           VARCHAR(150) NOT NULL,
    fecha_nacimiento          DATE,
    correo_electronico        VARCHAR(150) NOT NULL UNIQUE,
    contrasena                VARCHAR(100) NOT NULL,       -- BCrypt
    fecha_creacion_cuenta     DATE         NOT NULL DEFAULT CURRENT_DATE,
    token                     VARCHAR(100),                -- SHA-256 del refresh token vigente (sesion)
    active                    BOOLEAN      NOT NULL DEFAULT FALSE,
    notificacion_leida        BOOLEAN      NOT NULL DEFAULT FALSE,
    inmueble_id               BIGINT REFERENCES inmuebles (id),   -- Residente y UsuarioAutorizado
    admin_inicio_contrato     DATE,
    admin_vence_contrato      DATE,
    vigilante_inicio_contrato DATE,
    vigilante_vence_contrato  DATE,
    vence_autorizacion        DATE,                               -- UsuarioAutorizado
    CONSTRAINT ck_usuarios_tipo CHECK (tipo_usuario IN
        ('ADMINISTRADOR', 'RESIDENTE', 'VIGILANTE', 'USUARIO_AUTORIZADO')),
    CONSTRAINT ck_usuarios_inmueble CHECK
        (tipo_usuario NOT IN ('RESIDENTE', 'USUARIO_AUTORIZADO') OR inmueble_id IS NOT NULL)
);
CREATE INDEX ix_usuarios_inmueble ON usuarios (inmueble_id);
CREATE INDEX ix_usuarios_tipo ON usuarios (tipo_usuario);

CREATE TABLE parqueaderos (
    id            BIGSERIAL     PRIMARY KEY,
    inmueble_id   BIGINT        NOT NULL UNIQUE REFERENCES inmuebles (id) ON DELETE CASCADE,
    valor_mensual NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (valor_mensual >= 0)
);

CREATE TABLE autos (
    id             BIGSERIAL PRIMARY KEY,
    dueno_id       BIGINT UNIQUE REFERENCES usuarios (id) ON DELETE SET NULL,
    placa          BIGINT    NOT NULL UNIQUE,
    parqueadero_id BIGINT UNIQUE REFERENCES parqueaderos (id) ON DELETE SET NULL
);

CREATE TABLE zonas_comunes (
    id                BIGSERIAL    PRIMARY KEY,
    nombre_zona_comun VARCHAR(100) NOT NULL UNIQUE,
    capacidad         INTEGER      NOT NULL CHECK (capacidad > 0),
    hora_inicio       TIME,
    hora_fin          TIME,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE inmueble_zonas_comunes (
    inmueble_id  BIGINT NOT NULL REFERENCES inmuebles (id) ON DELETE CASCADE,
    zona_comun_id BIGINT NOT NULL REFERENCES zonas_comunes (id) ON DELETE CASCADE,
    PRIMARY KEY (inmueble_id, zona_comun_id)
);

CREATE TABLE reglas_reserva (
    id                         BIGSERIAL     PRIMARY KEY,
    anticipacion_minima_horas  INTEGER       NOT NULL CHECK (anticipacion_minima_horas >= 0),
    duracion_maxima_horas      INTEGER       NOT NULL CHECK (duracion_maxima_horas > 0),
    horas_antelacion_penalidad INTEGER       NOT NULL DEFAULT 0 CHECK (horas_antelacion_penalidad >= 0),
    tarifa_penalidad           NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tarifa_penalidad >= 0),
    admin_id                   BIGINT        NOT NULL REFERENCES usuarios (id)
);

CREATE TABLE rompimientos (
    id               BIGSERIAL    PRIMARY KEY,
    zona_comun_id    BIGINT       NOT NULL REFERENCES zonas_comunes (id) ON DELETE CASCADE,
    regla_reserva_id BIGINT       NOT NULL REFERENCES reglas_reserva (id) ON DELETE CASCADE,
    fecha_aplicacion DATE,
    activa           BOOLEAN      NOT NULL DEFAULT TRUE,
    observaciones    VARCHAR(500),
    CONSTRAINT uq_rompimientos UNIQUE (zona_comun_id, regla_reserva_id)
);
CREATE INDEX ix_rompimientos_zona ON rompimientos (zona_comun_id);

CREATE TABLE reservas_zona_comun (
    id                BIGSERIAL PRIMARY KEY,
    dueno_reserva_id  BIGINT    NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    zona_comun_id     BIGINT    NOT NULL REFERENCES zonas_comunes (id) ON DELETE CASCADE
);
CREATE INDEX ix_reservas_dueno ON reservas_zona_comun (dueno_reserva_id);
CREATE INDEX ix_reservas_zona ON reservas_zona_comun (zona_comun_id);

CREATE TABLE liquidaciones (
    id                      BIGSERIAL     PRIMARY KEY,
    inmueble_id             BIGINT        NOT NULL REFERENCES inmuebles (id) ON DELETE CASCADE,
    periodo                 DATE          NOT NULL,
    fecha_generacion        DATE,
    fecha_limite            DATE,
    cuota_administracion    NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (cuota_administracion >= 0),
    valor_servicios_comunes NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (valor_servicios_comunes >= 0),
    valor_multas            NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (valor_multas >= 0),
    total                   NUMERIC(14,2) NOT NULL DEFAULT 0,
    estado                  VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'AL_DIA', 'EN_MORA')),
    CONSTRAINT uq_liquidacion_inmueble_periodo UNIQUE (inmueble_id, periodo)
);
CREATE INDEX ix_liquidaciones_estado ON liquidaciones (estado);

ALTER TABLE inmuebles
    ADD CONSTRAINT fk_inmuebles_liquidacion_actual
    FOREIGN KEY (liquidacion_actual_id) REFERENCES liquidaciones (id) ON DELETE SET NULL;

CREATE TABLE pasarelas_pago (
    id              BIGSERIAL    PRIMARY KEY,
    nombre_pasarela VARCHAR(60)  NOT NULL,
    url_api         VARCHAR(300) NOT NULL,
    api_key         VARCHAR(200) NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'INACTIVA'))
);

CREATE TABLE pagos (
    id          BIGSERIAL     PRIMARY KEY,
    inmueble_id BIGINT        NOT NULL REFERENCES inmuebles (id),
    pasarela_id BIGINT        REFERENCES pasarelas_pago (id),
    monto       NUMERIC(14,2) NOT NULL CHECK (monto > 0),
    fecha_pago  DATE,
    concepto    VARCHAR(200),
    metodo_pago VARCHAR(30),
    estado      VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'ANULADO', 'REEMBOLSADO')),
    referencia  VARCHAR(100)  UNIQUE
);
CREATE INDEX ix_pagos_inmueble ON pagos (inmueble_id);
CREATE INDEX ix_pagos_estado ON pagos (estado);

CREATE TABLE notificaciones (
    id                BIGSERIAL    PRIMARY KEY,
    mensaje           VARCHAR(500) NOT NULL,
    tipo_notificacion VARCHAR(30)  NOT NULL,
    fecha_envio       TIMESTAMP
);

CREATE TABLE notificacion_usuarios (
    notificacion_id BIGINT NOT NULL REFERENCES notificaciones (id) ON DELETE CASCADE,
    usuario_id      BIGINT NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    PRIMARY KEY (notificacion_id, usuario_id)
);
CREATE INDEX ix_notificacion_usuarios_usuario ON notificacion_usuarios (usuario_id);

CREATE TABLE inmueble_notificaciones (
    inmueble_id     BIGINT NOT NULL REFERENCES inmuebles (id) ON DELETE CASCADE,
    notificacion_id BIGINT NOT NULL REFERENCES notificaciones (id) ON DELETE CASCADE,
    PRIMARY KEY (inmueble_id, notificacion_id)
);

CREATE TABLE residente_invitados (
    residente_id BIGINT NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    invitado_id  BIGINT NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    PRIMARY KEY (residente_id, invitado_id)
);
CREATE INDEX ix_residente_invitados_invitado ON residente_invitados (invitado_id);
