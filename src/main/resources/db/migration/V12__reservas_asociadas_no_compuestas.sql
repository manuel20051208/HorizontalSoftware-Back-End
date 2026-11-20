-- =============================================================================
-- V12: ReservaZonaComun -> ZonaComun y ReservaZonaComun -> Inmueble son ASOCIACIONES en el diagrama de clases
-- (flecha simple, sin rombo). La unica composicion de la reserva es Residente ◆— ReservaZonaComun.
--
-- V1/V4 dejaron reservas_zona_comun.zona_comun_id en ON DELETE CASCADE y V11 creo inmueble_id tambien en
-- CASCADE: borrar una zona o un inmueble borraba en silencio sus reservas. Con asociacion el borrado es normal
-- (NO ACTION): la base impide borrar la zona o el inmueble mientras tengan reservas, y el servicio responde 409.
-- reservas_zona_comun.dueno_reserva_id sigue en CASCADE (composicion con el Residente).
-- V1-V11 ya estan aplicadas y no se tocan.
-- =============================================================================

-- Recrea la llave foranea de tabla.columna con la accion de borrado indicada.
CREATE OR REPLACE FUNCTION pg_temp.fijar_borrado(p_tabla TEXT, p_columna TEXT, p_referencia TEXT, p_accion TEXT)
RETURNS VOID AS $$
DECLARE
    v_nombre TEXT;
BEGIN
    SELECT c.conname INTO v_nombre
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
    WHERE c.contype = 'f'
      AND c.conrelid = p_tabla::regclass
      AND a.attname = p_columna
      AND c.confrelid = p_referencia::regclass;
    IF v_nombre IS NULL THEN
        RAISE EXCEPTION 'No existe la llave foranea %.% -> %', p_tabla, p_columna, p_referencia;
    END IF;
    EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', p_tabla, v_nombre);
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I (id) ON DELETE %s',
                   p_tabla, v_nombre, p_columna, p_referencia, p_accion);
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.fijar_borrado('reservas_zona_comun', 'zona_comun_id', 'zonas_comunes', 'NO ACTION');  -- ReservaZonaComun -> ZonaComun
SELECT pg_temp.fijar_borrado('reservas_zona_comun', 'inmueble_id', 'inmuebles', 'NO ACTION');        -- ReservaZonaComun -> Inmueble
