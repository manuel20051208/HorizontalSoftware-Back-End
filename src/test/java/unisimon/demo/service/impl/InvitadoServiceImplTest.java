package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.policy.GeneradorContrasena;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.InvitadoRepository;
import unisimon.demo.repository.InvitadoRepository.InvitadoView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.service.support.InmuebleDelResidente;
import unisimon.demo.service.support.UsuarioPersistidor;
import unisimon.demo.support.Proyecciones;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** GET /api/residente/invitados: solo los invitados del residente relacionados con el inmueble elegido. */
@ExtendWith(MockitoExtension.class)
class InvitadoServiceImplTest {

    @Mock
    private InvitadoRepository invitados;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private InmuebleDelResidente inmuebleDelResidente;
    @Mock
    private UsuarioPersistidor persistidor;
    @Mock
    private PasswordEncoder codificador;
    @Mock
    private GeneradorContrasena generador;

    private InvitadoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);
        servicio = new InvitadoServiceImpl(invitados, usuarios, inmuebleDelResidente, persistidor, codificador,
                generador, reloj);
    }

    private InmuebleBasicoView inmueble(Long id, String direccion) {
        return Proyecciones.de(InmuebleBasicoView.class, "id", id, "direccionInmueble", direccion, "isActive", true);
    }

    private InvitadoView invitado(Long id, String inmuebles) {
        return Proyecciones.de(InvitadoView.class, "id", id, "identificacion", 2000L + id,
                "nombreCompleto", "Mariana", "correoElectronico", "m" + id + "@mail.com", "active", true,
                "venceAutorizacion", LocalDate.of(2026, 12, 31), "inmuebles", inmuebles);
    }

    @Test
    @DisplayName("lista solo los invitados del inmueble elegido y cuenta solo esos")
    void listarFiltraPorInmuebleElegido() {
        SolicitudPagina pagina = SolicitudPagina.primera();
        when(inmuebleDelResidente.resolver(5L, 3L)).thenReturn(inmueble(3L, "C-101"));
        when(invitados.listarDeResidenteEnInmueble(5L, 3L, 20, 0L))
                .thenReturn(List.of(invitado(7L, "3|C-101")));
        when(invitados.contarDeResidenteEnInmueble(5L, 3L)).thenReturn(1L);

        var resultado = servicio.listarInvitados(5L, 3L, pagina);

        assertThat(resultado.totalElementos()).isEqualTo(1L);
        assertThat(resultado.contenido()).singleElement().satisfies(invitado -> {
            assertThat(invitado.id()).isEqualTo(7L);
            assertThat(invitado.inmuebles()).singleElement()
                    .satisfies(i -> assertThat(i.id()).isEqualTo(3L));
        });
        verify(invitados, never()).listarDeResidenteEnInmueble(5L, 2L, 20, 0L);
        verify(invitados, never()).contarDeResidenteEnInmueble(5L, 2L);
    }

    @Test
    @DisplayName("sin selector se usa el unico inmueble del residente")
    void listarSinSelector() {
        SolicitudPagina pagina = SolicitudPagina.primera();
        when(inmuebleDelResidente.resolver(5L, null)).thenReturn(inmueble(2L, "B-201"));
        when(invitados.listarDeResidenteEnInmueble(5L, 2L, 20, 0L))
                .thenReturn(List.of(invitado(8L, "2|B-201")));
        when(invitados.contarDeResidenteEnInmueble(5L, 2L)).thenReturn(1L);

        var resultado = servicio.listarInvitados(5L, null, pagina);

        assertThat(resultado.contenido()).hasSize(1);
        verify(invitados).listarDeResidenteEnInmueble(5L, 2L, 20, 0L);
        verify(invitados).contarDeResidenteEnInmueble(5L, 2L);
    }

    @Test
    @DisplayName("un inmueble ajeno (403) o la cuenta sin inmuebles (409) no consulta invitados")
    void listarRechazosDelSelector() {
        when(inmuebleDelResidente.resolver(5L, 9L)).thenThrow(new ProhibidoException("Ese inmueble no es suyo"));
        when(inmuebleDelResidente.resolver(5L, null))
                .thenThrow(new ConflictoException("Primero relacione la cuenta con un inmueble"));

        assertThatThrownBy(() -> servicio.listarInvitados(5L, 9L, SolicitudPagina.primera()))
                .isInstanceOf(ProhibidoException.class)
                .hasMessageContaining("no es suyo");
        assertThatThrownBy(() -> servicio.listarInvitados(5L, null, SolicitudPagina.primera()))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Primero relacione la cuenta con un inmueble");
        verifyNoInteractions(invitados);
    }
}
