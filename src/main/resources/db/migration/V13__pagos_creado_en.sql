-- =============================================================================
-- V13: momento en que se creo cada pago.
--
-- Los pagos en linea nacen PENDIENTES con fecha_pago NULL (la fecha se pone al aprobarse), asi que no habia
-- forma de saber cuanto llevaban esperando. La sincronizacion con Wompi necesita ese dato para anular los
-- pagos abandonados (el residente cerro el checkout y Wompi nunca creo la transaccion).
-- Los pagos anteriores toman su fecha_pago o, si no tienen, el momento de esta migracion.
-- =============================================================================

ALTER TABLE pagos ADD COLUMN creado_en TIMESTAMP;

UPDATE pagos SET creado_en = COALESCE(fecha_pago::timestamp, CURRENT_TIMESTAMP) WHERE creado_en IS NULL;

CREATE INDEX ix_pagos_pendientes_en_linea ON pagos (creado_en) WHERE estado = 'PENDIENTE' AND referencia IS NOT NULL;
