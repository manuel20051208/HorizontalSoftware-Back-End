-- =============================================================================
-- V10: un residente puede quedar sin inmueble al desvincular su cuenta.
--
-- Hasta V9, ck_usuarios_inmueble exigia inmueble a RESIDENTE y a USUARIO_AUTORIZADO. Ahora el administrador
-- puede desvincular a un residente de su inmueble (DELETE /api/admin/usuarios/{identificacion}/inmueble): la
-- cuenta queda inactiva y sin inmueble. Un invitado (USUARIO_AUTORIZADO) siempre sigue a un residente, asi que
-- para el la restriccion se mantiene. V1-V9 ya estan aplicadas y no se tocan.
-- =============================================================================

ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_inmueble;

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_inmueble CHECK
    (tipo_usuario <> 'USUARIO_AUTORIZADO' OR inmueble_id IS NOT NULL);
