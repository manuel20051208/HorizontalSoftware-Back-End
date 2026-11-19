package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mail.CorreoSender;
import unisimon.demo.policy.GeneradorCodigo;
import unisimon.demo.repository.CodigoVerificacionRepository;
import unisimon.demo.repository.CodigoVerificacionRepository.CodigoView;
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
class CodigoVerificacionServiceImplTest {

    /** 2026-11-08 10:00 en Bogota. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 8, 10, 0);

    @Mock
    private CodigoVerificacionRepository codigos;
    @Mock
    private GeneradorCodigo generador;
    @Mock
    private PasswordEncoder codificador;
    @Mock
    private CorreoSender correo;

    private CodigoVerificacionServiceImpl servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Bogota"));
        servicio = new CodigoVerificacionServiceImpl(codigos, generador, codificador, correo, reloj);
    }

    private CodigoView vigente(int intentos, LocalDateTime vence) {
        return Proyecciones.de(CodigoView.class, "codigo", "hash-1234", "intentos", intentos, "venceEn", vence);
    }

    @Test
    @DisplayName("emitir guarda el codigo cifrado con vigencia de 15 minutos y lo envia en claro solo por correo")
    void emitir() {
        when(generador.generar()).thenReturn("0427");
        when(codificador.encode("0427")).thenReturn("hash-0427");

        servicio.emitir(9L, "ana@mail.com");

        verify(codigos).guardar(9L, "hash-0427", AHORA.plusMinutes(15));
        verify(correo).enviar(eq("ana@mail.com"), anyString(), contains("0427"));
    }

    @Test
    @DisplayName("verificar consume el codigo correcto")
    void correcto() {
        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(0, AHORA.plusMinutes(10))));
        when(codificador.matches("1234", "hash-1234")).thenReturn(true);

        servicio.verificar(9L, " 1234 ");

        verify(codigos).borrar(9L);
        verify(codigos, never()).sumarIntento(any());
    }

    @Test
    @DisplayName("un codigo incorrecto suma un intento y dice cuantos quedan")
    void incorrecto() {
        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(1, AHORA.plusMinutes(10))));
        when(codificador.matches("9999", "hash-1234")).thenReturn(false);

        assertThatThrownBy(() -> servicio.verificar(9L, "9999"))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("Quedan 3");
        verify(codigos).sumarIntento(9L);
        verify(codigos, never()).borrar(any());
    }

    @Test
    @DisplayName("el quinto fallo avisa que se agotaron los intentos y despues ni se compara el codigo")
    void agotado() {
        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(4, AHORA.plusMinutes(10))));
        when(codificador.matches("9999", "hash-1234")).thenReturn(false);
        assertThatThrownBy(() -> servicio.verificar(9L, "9999"))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("agotaron");

        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(5, AHORA.plusMinutes(10))));
        assertThatThrownBy(() -> servicio.verificar(9L, "1234"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("intentos");
        verify(codificador, never()).matches(eq("1234"), anyString());
    }

    @Test
    @DisplayName("rechaza un codigo vencido (pasados 15 minutos) o inexistente sin sumar intentos")
    void vencidoOInexistente() {
        when(codigos.buscar(9L)).thenReturn(Optional.of(vigente(0, AHORA.minusMinutes(1))));
        assertThatThrownBy(() -> servicio.verificar(9L, "1234"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("vencio");

        when(codigos.buscar(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.verificar(9L, "1234"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("envie uno nuevo");
        verify(codigos, never()).sumarIntento(any());
    }

    @Test
    @DisplayName("verificar corre en su propia transaccion y no deshace el intento fallido")
    void transaccionPropia() throws NoSuchMethodException {
        Transactional tx = CodigoVerificacionServiceImpl.class.getMethod("verificar", Long.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(tx.noRollbackFor()).contains(SolicitudInvalidaException.class);
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
    @DisplayName("el body de verificar-correo exige exactamente 4 cifras")
    void validacionDto() {
        var validador = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validador.validate(new UsuarioRequests.VerificarCorreo("0427"))).isEmpty();
        assertThat(validador.validate(new UsuarioRequests.VerificarCorreo("12a4"))).isNotEmpty();
        assertThat(validador.validate(new UsuarioRequests.VerificarCorreo("12345"))).isNotEmpty();
        assertThat(validador.validate(new UsuarioRequests.VerificarCorreo(""))).isNotEmpty();
    }
}
