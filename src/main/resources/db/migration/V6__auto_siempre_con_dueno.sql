-- =============================================================================
-- V6: Residente ◆— Auto con multiplicidad 1 en Residente (diagrama de clases, 2026-11-17).
--
-- La composicion exige que todo Auto tenga exactamente un dueno: autos.dueno_id pasa a NOT NULL.
-- La aplicacion ya crea siempre el auto con su residente (AutoServiceImpl) y, desde V4, borrar al residente
-- borra su auto (ON DELETE CASCADE), asi que no deberian existir autos sin dueno. Si quedara alguno de antes
-- de V4 (cuando la llave era ON DELETE SET NULL), esta migracion falla y no cambia nada: hay que asignarle
-- dueno o borrarlo a mano antes de volver a desplegar.
-- =============================================================================

ALTER TABLE autos ALTER COLUMN dueno_id SET NOT NULL;
