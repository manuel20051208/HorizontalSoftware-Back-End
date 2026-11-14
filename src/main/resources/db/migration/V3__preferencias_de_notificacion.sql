-- =============================================================================
-- V3: preferencias de notificacion por usuario (Usuario.preferenciasNotificacion).
--
-- Cada usuario guarda la lista de tipos de notificacion que quiere recibir
-- (GENERAL, RESERVA, PAGO_PENDIENTE, ...). La lista vacia significa "sin
-- preferencias": recibe todos los tipos, que es como venian funcionando las
-- cuentas creadas antes de esta version.
--
-- Las incidencias operativas de porteria se guardan como notificaciones de tipo
-- INCIDENCIA; el administrador las consulta por tipo.
-- =============================================================================

ALTER TABLE usuarios
    ADD COLUMN preferencias_notificacion VARCHAR(30)[] NOT NULL DEFAULT '{}';

CREATE INDEX ix_notificaciones_tipo ON notificaciones (tipo_notificacion, fecha_envio DESC);
