-- =============================================================================
-- V9: verificacion del correo con un codigo de 4 cifras; solo asi se activa una cuenta nueva.
--
-- Al crear una cuenta (POST /api/admin/usuarios) el back envia un codigo de 4 cifras al correo que escribio el
-- administrador. El usuario se lo dicta y el administrador lo confirma desde su panel con
-- POST /api/admin/usuarios/{identificacion}/verificar-correo: el correo queda verificado y la cuenta activa.
-- Ya no existe POST /api/auth/activar. El codigo:
--   * se guarda cifrado (BCrypt), nunca en claro;
--   * vence a los 15 minutos;
--   * admite como maximo 5 intentos fallidos; despues hay que pedir otro (reenviar-codigo), que reemplaza al
--     anterior y reinicia los intentos.
-- La tabla de codigos no hace parte del modelo (Usuario no cambia). V7 y V8 ya estan aplicadas y no se tocan.
-- =============================================================================

CREATE TABLE codigos_verificacion (
    usuario_id BIGINT       PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    codigo     VARCHAR(100) NOT NULL,
    intentos   INTEGER      NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    vence_en   TIMESTAMP    NOT NULL
);

-- TRUE cuando el administrador confirmo el codigo enviado a ese correo. Sin esto la cuenta no se puede activar.
ALTER TABLE usuarios ADD COLUMN correo_verificado BOOLEAN NOT NULL DEFAULT FALSE;

-- Las cuentas que ya estaban activas siguen funcionando. Las inactivas quedan pendientes de verificar: el
-- administrador les reenvia el codigo desde su panel.
UPDATE usuarios SET correo_verificado = TRUE WHERE active = TRUE;
