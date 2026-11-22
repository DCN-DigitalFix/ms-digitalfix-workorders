package cl.duoc.ms_digitalfix_workorders.messaging.kafka;

import java.time.Instant;

/**
 * Evento de dominio publicado en digitalfix.workorders.events. Lo consumen
 * ms-digitalfix-audit (grupo audit-timeline) y ms-digitalfix-report (grupo
 * report-metrics); ambos duplican este mismo record en su propio paquete a
 * proposito, igual que Envelope con RabbitMQ: el contrato es el JSON, no la
 * clase Java.
 *
 * @param eventId         identificador unico del evento
 * @param eventType       ORDEN_CREADA | ESTADO_CAMBIADO
 * @param occurredAt      momento en que ocurrio el hecho de negocio
 * @param traceId         hilo de la peticion HTTP que lo origino
 * @param ordenId         orden de trabajo afectada; tambien es la clave del mensaje en Kafka
 * @param estadoAnterior  null cuando eventType es ORDEN_CREADA
 * @param estadoNuevo     estado resultante
 * @param cliente         nombre del cliente de la orden
 * @param tecnicoAsignado null si todavia no se asigna cuadrilla
 * @param actor           subject del JWT de quien origino el cambio, o "sistema"
 */
public record WorkOrderEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String traceId,
        String ordenId,
        String estadoAnterior,
        String estadoNuevo,
        String cliente,
        String tecnicoAsignado,
        String actor) {

    public static final String ORDEN_CREADA = "ORDEN_CREADA";
    public static final String ESTADO_CAMBIADO = "ESTADO_CAMBIADO";
}
