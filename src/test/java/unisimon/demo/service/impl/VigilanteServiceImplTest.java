package unisimon.demo.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.repository.InvitadoRepository;
import unisimon.demo.repository.InvitadoRepository.InvitadoView;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.support.Proyecciones;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Vigilante.consultarInvitados(): listado paginado de los invitados registrados para la porteria. */
@ExtendWith(MockitoExtension.class)
class VigilanteServiceImplTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private ReservaZonaComunRepository reservas;
    @Mock
    private InvitadoRepository invitados;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private NotificacionService notificaciones;

    private VigilanteServiceImpl vigilante() {
        return new VigilanteServiceImpl(reservas, invitados, usuarios, notificaciones, RELOJ);
    }

    @Test
    @DisplayName("consultarInvitados pagina con LIMIT/OFFSET, cuenta el total y marca la vigencia de cada invitado")
    void consultarInvitados() {
        InvitadoView vigente = Proyecciones.de(InvitadoView.class, "id", 10L, "identificacion", 910L,
                "nombreCompleto", "Ana Invitada", "correoElectronico", "ana@mail.com", "active", true,
                "venceAutorizacion", LocalDate.now().plusDays(5));
        InvitadoView vencido = Proyecciones.de(InvitadoView.class, "id", 11L, "identificacion", 911L,
                "nombreCompleto", "Beto Invitado", "correoElectronico", "beto@mail.com", "active", true,
                "venceAutorizacion", LocalDate.now().minusDays(1));
        when(invitados.listarTodos(2, 2L)).thenReturn(List.of(vigente, vencido));
        when(invitados.contarTodos()).thenReturn(5L);

        Pagina<InvitadoResponses.Invitado> pagina = vigilante().consultarInvitados(new SolicitudPagina(1, 2));

        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(2);
        assertThat(pagina.totalElementos()).isEqualTo(5L);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
        assertThat(pagina.contenido()).extracting(InvitadoResponses.Invitado::id).containsExactly(10L, 11L);
        assertThat(pagina.contenido()).extracting(InvitadoResponses.Invitado::vigente).containsExactly(true, false);
        assertThat(pagina.contenido().get(0).nombreCompleto()).isEqualTo("Ana Invitada");
    }

    @Test
    @DisplayName("consultarInvitados sin invitados devuelve una pagina vacia con 0 paginas")
    void consultarInvitadosVacio() {
        when(invitados.listarTodos(20, 0L)).thenReturn(List.of());
        when(invitados.contarTodos()).thenReturn(0L);

        Pagina<InvitadoResponses.Invitado> pagina = vigilante().consultarInvitados(SolicitudPagina.primera());

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        assertThat(pagina.totalPaginas()).isZero();
    }
}
