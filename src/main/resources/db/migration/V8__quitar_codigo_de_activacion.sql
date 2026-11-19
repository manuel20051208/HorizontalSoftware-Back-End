-- =============================================================================
-- V8: se quita el codigo de activacion de 4 cifras (2026-11-19).
--
-- Decision de Manuel: la cuenta vuelve a activarse solo con correo y contrasena, como antes de V7. El panel del
-- administrador la activa al crearla. V7 ya se aplico en produccion, por eso no se borra: Flyway exige que
-- toda migracion aplicada siga existiendo. Esta migracion deshace su efecto.
-- =============================================================================

DROP TABLE IF EXISTS codigos_activacion;
