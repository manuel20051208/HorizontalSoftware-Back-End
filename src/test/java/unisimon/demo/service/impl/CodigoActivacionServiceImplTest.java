package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.mail.CorreoSender;
import unisimon.demo.policy.GeneradorCodigo;
import unisimon.demo.repository.CodigoActivacionRepository;
import unisimon.demo.repository.CodigoActivacionRepository.CodigoView;
import unisimon.demo.support.Proyecciones;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodigoActivacionServiceImplTest {

    /** 2026-11-08 10:00 en Bogota. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 8, 10, 0);

    @Mock
    private CodigoActivacionRepository codigos;
    @Mock
    private GeneradorCodigo generador;
    @Mock
    private PasswordEncoder codificador;
    @Mock
    private CorreoSender correo;

    private CodigoActivacionServiceImpl servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Bogota"));
        servicio = new CodigoActivacionServiceImpl(codigos, generador, codificador, correo, reloj);
    }

    private CodigoView vigente(int intentos, LocalDateTime vence) {
        return Proyecciones.de(CodigoView.class, "codigo", "hash-1234", "intentos", intentos, "venceEn", vence);
    }

    @Test
    @DisplayName("emitir guarda el codigo cifrado con vigencia de 7 dias y lo envia en claro solo por correo")
    void emitir() {
        when(generador.generar()).thenReturn("0427");
        when(codificador.encode("0427")).thenReturn("hash-0427");

        servicio.emitir(9L, "ana@mail.com");

        verify(codigos).guardar(9L, "hash-0427", AHORA.plusDays(7));
        verify(correo).enviar(eq("ana@mail.com"), anyString(), contains("0427"));
    }

    @Test
    @DisplayName("verificar consume el codigo correcto")
    void correcto() {
        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(0, AHORA.plusDays(3))));
        when(codificador.matches("1234", "hash-1234")).thenReturn(true);

        servicio.verificar(9L, " 1234 ");

        verify(codigos).borrar(9L);
        verify(codigos, never()).sumarIntento(any());
    }

    @Test
    @DisplayName("un codigo incorrecto suma un intento y dice cuantos quedan")
    void incorrecto() {
        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(1, AHORA.plusDays(3))));
        when(codificador.matches("9999", "hash-1234")).thenReturn(false);

        assertThatThrownBy(() -> servicio.verificar(9L, "9999"))
                .isInstanceOf(NoAutenticadoException.class).hasMessageContaining("quedan 3");
        verify(codigos).sumarIntento(9L);
        verify(codigos, never()).borrar(any());
    }

    @Test
    @DisplayName("el quinto fallo avisa que se agotaron los intentos y despues ni se compara el codigo")
    void agotado() {
        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(4, AHORA.plusDays(3))));
        when(codificador.matches("9999", "hash-1234")).thenReturn(false);
        assertThatThrownBy(() -> servicio.verificar(9L, "9999"))
                .isInstanceOf(NoAutenticadoException.class).hasMessageContaining("agotaron");

        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(5, AHORA.plusDays(3))));
        assertThatThrownBy(() -> servicio.verificar(9L, "1234"))
                .isInstanceOf(ProhibidoException.class).hasMessageContaining("intentos");
        verify(codificador, never()).matches(eq("1234"), anyString());
    }

    @Test
    @DisplayName("rechaza un codigo vencido o inexistente sin sumar intentos")
    void vencidoOInexistente() {
        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(0, AHORA.minusMinutes(1))));
        assertThatThrownBy(() -> servicio.verificar(9L, "1234"))
                .isInstanceOf(ProhibidoException.class).hasMessageContaining("vencio");

        when(codigos.buscar(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.verificar(9L, "1234"))
                .isInstanceOf(ProhibidoException.class).hasMessageContaining("pida uno nuevo");
        verify(codigos, never()).sumarIntento(any());
    }

    @Test
    @DisplayName("GeneradorCodigo siempre da 4 cifras, con ceros a la izquierda")
    void generador() {
        GeneradorCodigo real = new GeneradorCodigo();
        for (int i = 0; i < 500; i++) {
            assertThat(real.generar()).matches("[0-9]{4}");
        }
    }

    @Test
    @DisplayName("el body de /activar exige exactamente 4 cifras en el codigo")
    void validacionDto() {
        var validador = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var bien = new unisimon.demo.dto.request.AuthRequests.Activar("a@mail.com", "Clave12345", "0427");
        var mal = new unisimon.demo.dto.request.AuthRequests.Activar("a@mail.com", "Clave12345", "12a4");
        var largo = new unisimon.demo.dto.request.AuthRequests.Activar("a@mail.com", "Clave12345", "12345");
        assertThat(validador.validate(bien)).isEmpty();
        assertThat(validador.validate(mal)).isNotEmpty();
        assertThat(validador.validate(largo)).isNotEmpty();
    }
}
