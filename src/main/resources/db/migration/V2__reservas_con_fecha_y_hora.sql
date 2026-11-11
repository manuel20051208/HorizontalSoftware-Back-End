-- =============================================================================
-- V2: las reservas de zonas comunes pasan a tener fecha y hora de inicio y fin.
--
-- Antes una reserva no guardaba cuando era: el cupo contaba todas las reservas
-- de la zona y nunca terminaban. Ahora cada reserva ocupa un intervalo
-- [fecha_hora_inicio, fecha_hora_fin) y su estado (PROGRAMADA, EN_CURSO,
-- FINALIZADA) se calcula comparando ese intervalo con la hora actual.
--
-- Las horas se guardan como TIMESTAMP sin zona, en hora de Colombia
-- (America/Bogota), igual que el reloj de la aplicacion.
-- =============================================================================

ALTER TABLE reservas_zona_comun
    ADD COLUMN fecha_hora_inicio TIMESTAMP,
    ADD COLUMN fecha_hora_fin    TIMESTAMP;

-- Las reservas creadas antes de esta version no tienen fecha y no hay forma de
-- saber cuando eran: se cierran como FINALIZADAS (una hora que termina en el
-- momento de migrar) para que no ocupen cupo ni aparezcan como pendientes.
UPDATE reservas_zona_comun
SET fecha_hora_inicio = date_trunc('minute', now() AT TIME ZONE 'America/Bogota') - INTERVAL '1 hour',
    fecha_hora_fin    = date_trunc('minute', now() AT TIME ZONE 'America/Bogota')
WHERE fecha_hora_inicio IS NULL;

ALTER TABLE reservas_zona_comun
    ALTER COLUMN fecha_hora_inicio SET NOT NULL,
    ALTER COLUMN fecha_hora_fin    SET NOT NULL,
    ADD CONSTRAINT ck_reservas_intervalo CHECK (fecha_hora_fin > fecha_hora_inicio);

-- Cupo y solapes se consultan por zona e intervalo; los listados ordenan por inicio.
CREATE INDEX ix_reservas_zona_intervalo ON reservas_zona_comun (zona_comun_id, fecha_hora_inicio, fecha_hora_fin);
CREATE INDEX ix_reservas_dueno_inicio ON reservas_zona_comun (dueno_reserva_id, fecha_hora_inicio);
