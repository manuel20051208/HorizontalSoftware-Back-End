-- =============================================================================
-- V7: codigo de verificacion de 4 cifras para activar la cuenta (2026-11-19).
--
-- Al crear una cuenta (POST /api/admin/usuarios) se envia por correo un codigo de 4 cifras. Para activarla,
-- POST /api/auth/activar pide el correo, la contrasena y ese codigo. El codigo:
--   * se guarda cifrado (BCrypt), nunca en claro;
--   * vence a los 7 dias, la misma ventana que tiene la activacion;
--   * admite como maximo 5 intentos fallidos; despues queda invalido y hay que pedir otro con
--     POST /api/auth/reenviar-codigo.
-- Tiene su propia tabla porque no hace parte del modelo (Usuario no cambia). Al borrar al usuario se borra su
-- codigo (ON DELETE CASCADE). Un solo codigo vigente por usuario: pedir otro reemplaza el anterior.
-- =============================================================================

CREATE TABLE codigos_activacion (
    usuario_id BIGINT       PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    codigo     VARCHAR(100) NOT NULL,
    intentos   INTEGER      NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    vence_en   TIMESTAMP    NOT NULL
);
