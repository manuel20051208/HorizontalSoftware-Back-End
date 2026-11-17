-- =============================================================================
-- V5: Inmueble ◇— Residente pasa de composicion a AGREGACION (Inmueble sigue siendo el todo) (diagrama de clases, 2026-11-16).
--
-- Antes (V4) era composicion Inmueble -> Residente: usuarios.inmueble_id con ON DELETE CASCADE, asi que
-- borrar un inmueble borraba a sus residentes. Con agregacion el borrado es normal (NO ACTION): no se borra
-- nada en cascada y la base impide borrar un inmueble que todavia tiene residentes (o invitados) asignados.
--
-- Parqueadero - Auto sigue diferida (V4); ya no hace falta para esta cascada, pero no estorba.
-- Se reutiliza la misma logica de V4: recrear la llave foranea con la accion de borrado indicada.
-- =============================================================================

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

SELECT pg_temp.fijar_borrado('usuarios', 'inmueble_id', 'inmuebles', 'NO ACTION');   -- Inmueble ◇— Residente
