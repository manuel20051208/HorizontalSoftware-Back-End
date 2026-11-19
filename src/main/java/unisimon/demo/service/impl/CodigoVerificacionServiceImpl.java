package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mail.CorreoSender;
import unisimon.demo.policy.GeneradorCodigo;
import unisimon.demo.repository.CodigoVerificacionRepository;
import unisimon.demo.repository.CodigoVerificacionRepository.CodigoView;
import unisimon.demo.service.CodigoVerificacionService;

import java.time.Clock;
import java.time.LocalDateTime;

/** Emite y verifica el codigo de verificacion del correo: cifrado, con vencimiento y con un maximo de intentos. */
@Service
@RequiredArgsConstructor
public class CodigoVerificacionServiceImpl implements CodigoVerificacionService {

    /** El usuario se lo dicta al administrador en el momento: no necesita durar mas. */
    static final int MINUTOS_VIGENCIA = 15;
    /** Con 4 cifras hay 10.000 combinaciones: sin limite se podria adivinar. */
    static final int MAX_INTENTOS = 5;

    private final CodigoVerificacionRepository codigos;
    private final GeneradorCodigo generador;
    private final PasswordEncoder codificador;
    private final CorreoSender correo;
    private final Clock reloj;

    @Override
    @Transactional
    public void emitir(Long usuarioId, String destino) {
        String codigo = generador.generar();
        codigos.guardar(usuarioId, codificador.encode(codigo), LocalDateTime.now(reloj).plusMinutes(MINUTOS_VIGENCIA));
        correo.enviar(destino, "Codigo de verificacion - Torres del Parque",
                "Su codigo para verificar el correo y activar la cuenta es: " + codigo + ". Dictelo a la administracion."
                        + " Vence en " + MINUTOS_VIGENCIA + " minutos. Si usted no pidio esta cuenta, ignore este correo.");
    }

    /**
     * REQUIRES_NEW: el intento fallido se guarda aunque la verificacion que llama a este metodo termine en error
     * y deshaga su propia transaccion. Si no, los intentos nunca se acumularian. Se llama desde otro bean, asi que
     * pasa por el proxy y la propagacion se respeta.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = SolicitudInvalidaException.class)
    public void verificar(Long usuarioId, String codigo) {
        CodigoView vigente = codigos.buscar(usuarioId)
                .orElseThrow(() -> new ConflictoException("No hay un codigo vigente: envie uno nuevo"));
        if (LocalDateTime.now(reloj).isAfter(vigente.getVenceEn())) {
            throw new ConflictoException("El codigo vencio: envie uno nuevo");
        }
        if (vigente.getIntentos() >= MAX_INTENTOS) {
            throw new ConflictoException("Se agotaron los intentos del codigo: envie uno nuevo");
        }
        if (codigo == null || !codificador.matches(codigo.trim(), vigente.getCodigo())) {
            codigos.sumarIntento(usuarioId);
            int quedan = MAX_INTENTOS - vigente.getIntentos() - 1;
            throw new SolicitudInvalidaException(quedan > 0
                    ? "Codigo incorrecto. Quedan " + quedan + " intentos"
                    : "Codigo incorrecto. Se agotaron los intentos: envie uno nuevo");
        }
        codigos.borrar(usuarioId);
    }
}
