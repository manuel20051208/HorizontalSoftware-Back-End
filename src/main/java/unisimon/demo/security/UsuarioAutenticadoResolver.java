package unisimon.demo.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import unisimon.demo.exception.NoAutenticadoException;

/** Resuelve el parametro {@link UsuarioAutenticado} de los controladores a partir del JWT de la peticion. */
public class UsuarioAutenticadoResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return UsuarioAutenticado.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer contenedor,
                                  NativeWebRequest peticion, WebDataBinderFactory fabrica) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            return new UsuarioAutenticado(Long.valueOf(jwt.getToken().getSubject()),
                    jwt.getToken().getClaimAsString(JwtTokenService.CLAIM_ROL));
        }
        throw new NoAutenticadoException("Debe iniciar sesion");
    }
}
