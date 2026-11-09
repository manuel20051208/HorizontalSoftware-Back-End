package unisimon.demo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La proteccion va por anotacion (no por URL): ningun endpoint puede quedar sin etiqueta de seguridad. */
class EndpointsProtegidosTest {

    @Test
    @DisplayName("todo metodo de controlador lleva una anotacion de seguridad (@PreAuthorize o su meta-anotacion)")
    void ningunEndpointSinEtiqueta() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> sinProteger = new ArrayList<>();
        int endpoints = 0;
        for (BeanDefinition definicion : escaner.findCandidateComponents("unisimon.demo.controller")) {
            Class<?> controlador = Class.forName(definicion.getBeanClassName());
            for (Method metodo : controlador.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(metodo, RequestMapping.class)) {
                    continue;
                }
                endpoints++;
                if (!AnnotatedElementUtils.hasAnnotation(metodo, PreAuthorize.class)) {
                    sinProteger.add(controlador.getSimpleName() + "." + metodo.getName());
                }
            }
        }
        assertThat(endpoints).as("endpoints encontrados").isGreaterThan(50);
        assertThat(sinProteger).as("endpoints sin anotacion de seguridad").isEmpty();
    }
}
