package cl.duoc.ms_digitalfix_workorders.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre comun a todos los mensajes del sistema.
 *
 * Es la misma forma que espera ms-digitalfix-notify. Se duplica el record en
 * ambos repositorios a proposito: compartir una libreria entre microservicios
 * los acopla, y entonces un cambio de formato obliga a desplegarlos juntos.
 * El contrato es el JSON, no la clase Java.
 *
 * @param type          que ocurrio, p. ej. "orden.asignada"
 * @param eventId       identificador unico; el consumidor lo usa para deduplicar
 * @param timestamp     momento de emision
 * @param traceId       hilo de la peticion HTTP que lo origino
 * @param correlationId agrupa los mensajes de una misma orden de trabajo
 * @param datos         carga util propia del caso de uso
 */
public record Envelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        Map<String, Object> datos) {

    /**
     * Construye el sobre completando lo que es siempre igual: un eventId
     * nuevo y la marca de tiempo actual.
     *
     * @param type          tipo del evento
     * @param correlationId normalmente el id de la orden de trabajo
     * @param traceId       identificador de la peticion en curso
     * @param datos         carga util
     */
    public static Envelope de(
            String type, String correlationId, String traceId, Map<String, Object> datos) {

        return new Envelope(
                type,
                UUID.randomUUID().toString(),
                Instant.now(),
                traceId,
                correlationId,
                datos);
    }
}
