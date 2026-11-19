package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.mail.CorreoSender;
import unisimon.demo.policy.GeneradorCodigo;
import unisimon.demo.repository.CodigoActivacionRepository;
import unisimon.demo.repository.CodigoActivacionRepository.CodigoView;
import unisimon.demo.service.CodigoActivacionService;

import java.time.Clock;
import java.time.LocalDateTime;

/** Emite y verifica el codigo de activacion: cifrado, con vencimiento y con un maximo de intentos. */
@Service
@RequiredArgsConstructor
public class CodigoActivacionServiceImpl implements CodigoActivacionService {

    /** Igual que la ventana de activacion de la cuenta. */
    static final int DIAS_VIGENCIA = 7;
    /** Con 4 cifras hay 10.000 combinaciones: sin limite se podria adivinar. */
    static final int MAX_INTENTOS = 5;

    private final CodigoActivacionRepository codigos;
    private final GeneradorCodigo generador;
    private final PasswordEncoder codificador;
    private final CorreoSender correo;
    private final Clock reloj;

    @Override
    @Transactional
    public void emitir(Long usuarioId, String destino) {
        String codigo = generador.generar();
        codigos.guardar(usuarioId, codificador.encode(codigo), LocalDateTime.now(reloj).plusDays(DIAS_VIGENCIA));
        correo.enviar(destino, "Codigo de activacion - Torres del Parque",
                "Su codigo para activar la cuenta es: " + codigo + ". Vence en " + DIAS_VIGENCIA
                        + " dias y admite " + MAX_INTENTOS + " intentos. Si usted no pidio esta cuenta, ignore este correo.");
    }

    /**
     * REQUIRES_NEW: el intento fallido se guarda aunque la activacion que llama a este metodo termine en error
     * y deshaga su propia transaccion. Si no, los intentos nunca se acumularian.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = NoAutenticadoException.class)
    public void verificar(Long usuarioId, String codigo) {
        CodigoView vigente = codigos.buscar(usuarioId)
                .orElseThrow(() -> new ProhibidoException("No hay un codigo de activacion vigente: pida uno nuevo"));
        if (LocalDateTime.now(reloj).isAfter(vigente.getVenceEn())) {
            throw new ProhibidoException("El codigo de activacion vencio: pida uno nuevo");
        }
        if (vigente.getIntentos() >= MAX_INTENTOS) {
            throw new ProhibidoException("Se agotaron los intentos del codigo: pida uno nuevo");
        }
        if (codigo == null || !codificador.matches(codigo.trim(), vigente.getCodigo())) {
            codigos.sumarIntento(usuarioId);
            int quedan = MAX_INTENTOS - vigente.getIntentos() - 1;
            throw new NoAutenticadoException(quedan > 0
                    ? "Codigo incorrecto. Le quedan " + quedan + " intentos"
                    : "Codigo incorrecto. Se agotaron los intentos: pida uno nuevo");
        }
        codigos.borrar(usuarioId);
    }
}
