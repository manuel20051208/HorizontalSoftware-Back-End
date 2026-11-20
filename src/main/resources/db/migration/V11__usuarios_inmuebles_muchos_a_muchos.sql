-- =============================================================================
-- V11: residentes e invitados se relacionan con varios inmuebles (N:M).
--
-- Hasta V10 usuarios.inmueble_id era la unica fuente de verdad y una cuenta tenia un solo inmueble. Ahora la
-- relacion vive en usuario_inmuebles; cada reserva guarda el inmueble al que pertenece. Los vínculos son
-- agregacion: borrar el inmueble borra el vinculo, nunca al usuario. V1-V10 ya estan aplicadas y no se tocan.
-- =============================================================================

CREATE TABLE usuario_inmuebles (
    usuario_id  BIGINT NOT NULL REFERENCES usuarios (id)  ON DELETE CASCADE,
    inmueble_id BIGINT NOT NULL REFERENCES inmuebles (id) ON DELETE CASCADE,  -- agregación: borra el vínculo, no al usuario
    PRIMARY KEY (usuario_id, inmueble_id)
);
CREATE INDEX ix_usuario_inmuebles_inmueble ON usuario_inmuebles (inmueble_id);
INSERT INTO usuario_inmuebles (usuario_id, inmueble_id)
    SELECT id, inmueble_id FROM usuarios WHERE inmueble_id IS NOT NULL;

-- cada reserva es de UN apartamento del residente
ALTER TABLE reservas_zona_comun ADD COLUMN inmueble_id BIGINT REFERENCES inmuebles (id) ON DELETE CASCADE;
UPDATE reservas_zona_comun r SET inmueble_id = u.inmueble_id FROM usuarios u WHERE u.id = r.dueno_reserva_id;
DELETE FROM reservas_zona_comun WHERE inmueble_id IS NULL;   -- solo pasaría con dueños sin inmueble (hoy: ninguno)
ALTER TABLE reservas_zona_comun ALTER COLUMN inmueble_id SET NOT NULL;
CREATE INDEX ix_reservas_inmueble ON reservas_zona_comun (inmueble_id);

ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_inmueble;
ALTER TABLE usuarios DROP COLUMN inmueble_id;                -- única fuente de verdad: usuario_inmuebles
