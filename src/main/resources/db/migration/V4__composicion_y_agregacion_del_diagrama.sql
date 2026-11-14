-- =============================================================================
-- V4: el borrado en cascada sigue al pie de la letra el diagrama de clases.
--
--   Composicion (rombo lleno)   -> la llave foranea es ON DELETE CASCADE: borrar el "todo" borra sus partes.
--   Agregacion y asociacion     -> borrado normal (NO ACTION): no se borra nada en cascada y la base impide
--                                  borrar algo que todavia esta referenciado.
--
-- Composiciones del diagrama (todo -> parte):
--   Inmueble  -> Residente (usuarios.inmueble_id), Pago, Parqueadero, Liquidacion
--   Residente -> Auto, ReservaZonaComun
--   ZonaComun -> ReservaZonaComun
-- Agregaciones y asociaciones (borrado normal):
--   ReglaReserva - Rompimiento, ZonaComun - Rompimiento, ReglaReserva - Administrador,
--   Usuario - Notificacion, Inmueble - Notificacion, Inmueble - ZonaComun, Residente - UsuarioAutorizado,
--   Parqueadero - Auto, Pago - PasarelaPago.
--
-- Nota: usuarios.inmueble_id lo comparten Residente y UsuarioAutorizado (misma tabla); el diagrama solo
-- compone Inmueble -> Residente, pero la llave es una sola, asi que el CASCADE alcanza a ambos.
-- Ya estaban en CASCADE y no cambian: parqueaderos.inmueble_id, liquidaciones.inmueble_id,
-- reservas_zona_comun.dueno_reserva_id y reservas_zona_comun.zona_comun_id.
-- =============================================================================

-- Recrea la llave foranea de tabla.columna con la accion de borrado indicada (CASCADE o NO ACTION).
CREATE OR REPLACE FUNCTION pg_temp.fijar_borrado(p_tabla TEXT, p_columna TEXT, p_referencia TEXT, p_accion TEXT, p_diferida BOOLEAN DEFAULT FALSE)
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
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I (id) ON DELETE %s%s',
                   p_tabla, v_nombre, p_columna, p_referencia, p_accion,
                   CASE WHEN p_diferida THEN ' DEFERRABLE INITIALLY DEFERRED' ELSE '' END);
END;
$$ LANGUAGE plpgsql;

-- ---- composiciones: CASCADE -------------------------------------------------------------------------
SELECT pg_temp.fijar_borrado('usuarios', 'inmueble_id', 'inmuebles', 'CASCADE');          -- Inmueble -> Residente
SELECT pg_temp.fijar_borrado('pagos', 'inmueble_id', 'inmuebles', 'CASCADE');             -- Inmueble -> Pago
SELECT pg_temp.fijar_borrado('autos', 'dueno_id', 'usuarios', 'CASCADE');                 -- Residente -> Auto

-- ---- agregaciones y asociaciones: borrado normal ----------------------------------------------------
-- Parqueadero - Auto: la comprobacion se difiere al final de la transaccion; asi, al borrar un Inmueble, el
-- Auto que se va con su Residente (cascada) ya no estorba cuando se borra el Parqueadero (cascada).
SELECT pg_temp.fijar_borrado('autos', 'parqueadero_id', 'parqueaderos', 'NO ACTION', TRUE);
SELECT pg_temp.fijar_borrado('rompimientos', 'zona_comun_id', 'zonas_comunes', 'NO ACTION');          -- ZonaComun - Rompimiento
SELECT pg_temp.fijar_borrado('rompimientos', 'regla_reserva_id', 'reglas_reserva', 'NO ACTION');      -- ReglaReserva - Rompimiento
SELECT pg_temp.fijar_borrado('inmueble_zonas_comunes', 'inmueble_id', 'inmuebles', 'NO ACTION');      -- Inmueble - ZonaComun
SELECT pg_temp.fijar_borrado('inmueble_zonas_comunes', 'zona_comun_id', 'zonas_comunes', 'NO ACTION');
SELECT pg_temp.fijar_borrado('notificacion_usuarios', 'notificacion_id', 'notificaciones', 'NO ACTION'); -- Usuario - Notificacion
SELECT pg_temp.fijar_borrado('notificacion_usuarios', 'usuario_id', 'usuarios', 'NO ACTION');
SELECT pg_temp.fijar_borrado('inmueble_notificaciones', 'inmueble_id', 'inmuebles', 'NO ACTION');     -- Inmueble - Notificacion
SELECT pg_temp.fijar_borrado('inmueble_notificaciones', 'notificacion_id', 'notificaciones', 'NO ACTION');
SELECT pg_temp.fijar_borrado('residente_invitados', 'residente_id', 'usuarios', 'NO ACTION');         -- Residente - UsuarioAutorizado
SELECT pg_temp.fijar_borrado('residente_invitados', 'invitado_id', 'usuarios', 'NO ACTION');
