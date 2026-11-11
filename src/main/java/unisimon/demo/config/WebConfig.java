package unisimon.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import unisimon.demo.security.UsuarioAutenticadoResolver;

import java.util.List;

/** Registra los resolvedores que entregan el usuario autenticado y la pagina pedida a los controladores. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UsuarioAutenticadoResolver());
        resolvers.add(new SolicitudPaginaResolver());
    }
}
