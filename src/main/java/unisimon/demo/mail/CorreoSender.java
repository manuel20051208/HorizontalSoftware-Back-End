package unisimon.demo.mail;

/** Puerto de salida para enviar correos. Los servicios dependen de esta interfaz, no de un proveedor concreto. */
public interface CorreoSender {

    void enviar(String destino, String asunto, String cuerpo);
}
