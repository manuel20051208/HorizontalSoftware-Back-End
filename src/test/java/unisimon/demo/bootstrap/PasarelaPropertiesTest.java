package unisimon.demo.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasarelaPropertiesTest {

    private static final String CHECKOUT = "https://checkout.wompi.co/p/";

    private static PasarelaProperties pruebas(String redirect) {
        return new PasarelaProperties("Wompi", CHECKOUT, "pub_test_abc", "test_integrity_abc", "test_events_abc",
                "prv_test_abc", "https://sandbox.wompi.co/v1", redirect);
    }

    private static PasarelaProperties produccion() {
        return new PasarelaProperties("Wompi", CHECKOUT, "pub_prod_abc", "prod_integrity_abc", "prod_events_abc",
                "prv_prod_abc", "https://production.wompi.co/v1/", "https://front.example.com/residente/pagos");
    }

    @Test
    @DisplayName("configuracion completa de pruebas o de produccion: lista para cobrar")
    void completas() {
        assertThat(pruebas(null).problemas()).isEmpty();
        assertThat(pruebas("http://localhost:3000/residente/pagos").problemas()).isEmpty();
        assertThat(pruebas(null).ambiente()).isEqualTo(PasarelaProperties.PRUEBAS);
        assertThat(produccion().problemas()).isEmpty();
        assertThat(produccion().listaParaCobrar()).isTrue();
        assertThat(produccion().ambiente()).isEqualTo(PasarelaProperties.PRODUCCION);
    }

    @Test
    @DisplayName("detecta ambientes mezclados (llave de produccion con API de sandbox)")
    void ambientesMezclados() {
        PasarelaProperties mezclada = new PasarelaProperties("Wompi", CHECKOUT, "pub_prod_abc", "prod_integrity_abc",
                "prod_events_abc", "prv_prod_abc", "https://sandbox.wompi.co/v1", null);

        assertThat(mezclada.problemas()).singleElement().asString()
                .contains("Ambientes mezclados", "PASARELA_API_KEY=produccion", "PASARELA_URL_API_REST=pruebas");
    }

    @Test
    @DisplayName("detecta vacios, prefijos equivocados, placeholders y espacios sin mostrar los valores")
    void llavesInvalidas() {
        PasarelaProperties mala = new PasarelaProperties(" ", CHECKOUT, "pub_test_CAMBIAR_POR_LA_LLAVE_REAL",
                "", "prv_test_al_reves", "prv_test_abc \n", "https://sandbox.wompi.co/v1", null);

        assertThat(mala.problemas()).contains(
                "PASARELA_NOMBRE esta vacia",
                "PASARELA_API_KEY sigue con el valor de ejemplo",
                "PASARELA_INTEGRITY_SECRET esta vacia",
                "PASARELA_EVENTS_SECRET debe empezar por prod_events_ o test_events_",
                "PASARELA_PRIVATE_KEY tiene espacios o saltos de linea");
        assertThat(String.join(" ", mala.problemas())).doesNotContain("prv_test_al_reves", "prv_test_abc");
    }

    @Test
    @DisplayName("valida las URLs: checkout de Wompi, API REST /v1 y redirect https")
    void urlsInvalidas() {
        PasarelaProperties mala = new PasarelaProperties("Wompi", "http://checkout.wompi.co/p/", "pub_test_abc",
                "test_integrity_abc", "test_events_abc", "prv_test_abc", "https://api.otra.com/v1",
                "http://front.example.com/pagos");

        assertThat(mala.problemas()).contains(
                "PASARELA_URL_API debe ser una URL https valida",
                "PASARELA_URL_API_REST debe ser https://production.wompi.co/v1 o https://sandbox.wompi.co/v1",
                "PASARELA_REDIRECT_URL debe ser una URL https completa (http solo para localhost)");

        PasarelaProperties otroCheckout = new PasarelaProperties("Wompi", "https://pagos.falsos.com/p/",
                "pub_test_abc", "test_integrity_abc", "test_events_abc", "prv_test_abc",
                "https://sandbox.wompi.co", "no es url");
        assertThat(otroCheckout.problemas()).contains(
                "PASARELA_URL_API debe ser https://checkout.wompi.co/p/",
                "PASARELA_URL_API_REST debe ser https://production.wompi.co/v1 o https://sandbox.wompi.co/v1",
                "PASARELA_REDIRECT_URL debe ser una URL https completa (http solo para localhost)");
    }

    @Test
    @DisplayName("toString no imprime ningun secreto")
    void toStringSinSecretos() {
        assertThat(produccion().toString())
                .doesNotContain("prod_integrity_abc", "prod_events_abc", "prv_prod_abc").contains("****");
    }
}
