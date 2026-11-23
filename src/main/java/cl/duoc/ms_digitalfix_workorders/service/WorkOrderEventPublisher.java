package cl.duoc.ms_digitalfix_workorders.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cl.duoc.ms_digitalfix_workorders.config.MensajeriaProperties;
import cl.duoc.ms_digitalfix_workorders.entity.WorkOrderEntity;
import cl.duoc.ms_digitalfix_workorders.messaging.Envelope;

/**
 * Publicacion de eventos de orden de trabajo.
 *
 * Es el unico punto del microservicio que conoce RabbitTemplate.
 * WorkOrderService le informa un hecho — esta orden se creo, esta orden se
 * asigno — y no sabe a que exchange va, con que routing key, ni cuantos
 * consumidores reaccionan.
 *
 * Ningun metodo propaga excepciones. La mensajeria es secundaria respecto del
 * CRUD: si el broker esta caido, la orden igual se guarda y el usuario recibe
 * su 201. El fallo queda registrado en el log, no silenciado.
 */
@Service
public class WorkOrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderEventPublisher.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MensajeriaProperties props;

    /** Orden recien creada: se confirma su recepcion al cliente. */
    public void publicarCreada(WorkOrderEntity orden) {
        publicar(
                props.routingKeys().email(),
                Envelope.de("orden.creada", orden.getId(), nuevoTraceId(),
                        datosEmail(orden, "Recibimos su solicitud " + orden.getId())));
    }

    /**
     * Orden asignada a una cuadrilla.
     *
     * Dispara dos flujos desde un solo hecho: avisar al cliente y emitir el
     * ticket de terreno. Es donde se ve el desacoplamiento, porque este
     * microservicio no conoce a ninguno de los dos consumidores.
     */
    public void publicarAsignada(WorkOrderEntity orden) {
        String traceId = nuevoTraceId();

        publicar(props.routingKeys().email(),
                Envelope.de("orden.asignada", orden.getId(), traceId,
                        datosEmail(orden, "Su orden fue asignada a una cuadrilla")));

        publicar(props.routingKeys().dispatch(),
                Envelope.de("orden.asignada", orden.getId(), traceId,
                        datosDispatch(orden)));
    }

    /** Trabajo finalizado: se avisa al cliente y se genera el certificado. */
    public void publicarCerrada(WorkOrderEntity orden) {
        String traceId = nuevoTraceId();

        publicar(props.routingKeys().email(),
                Envelope.de("orden.cerrada", orden.getId(), traceId,
                        datosEmail(orden, "Trabajo finalizado")));

        publicar(props.routingKeys().report(),
                Envelope.de("orden.cerrada", orden.getId(), traceId,
                        datosReport(orden, "certificado")));
    }

    /** Orden anulada: solo se notifica. */
    public void publicarAnulada(WorkOrderEntity orden) {
        publicar(props.routingKeys().email(),
                Envelope.de("orden.anulada", orden.getId(), nuevoTraceId(),
                        datosEmail(orden, "Su orden fue anulada")));
    }

    // -----------------------------------------------------------------------

    /**
     * Envia al exchange direct. Un fallo se registra pero no se propaga: el
     * flujo HTTP que llamo a este metodo no debe romperse por el broker.
     */
    private void publicar(String routingKey, Envelope envelope) {
        if (!props.habilitada()) {
            log.debug("Mensajeria desactivada, no se publica {}", envelope.type());
            return;
        }

        try {
            rabbitTemplate.convertAndSend(props.exchanges().direct(), routingKey, envelope);

            log.info("Publicado {} para la orden {} en {} con clave {}",
                    envelope.type(), envelope.correlationId(),
                    props.exchanges().direct(), routingKey);

        } catch (AmqpException e) {
            log.error("No se pudo publicar {} para la orden {}: {}",
                    envelope.type(), envelope.correlationId(), e.getMessage());
        }
    }

    private Map<String, Object> datosEmail(WorkOrderEntity orden, String mensaje) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("ordenId", orden.getId());
        // El correo real vendria del perfil del cliente. Mientras ese dato no
        // exista se deriva del nombre, para que el flujo sea demostrable.
        datos.put("destinatario", correoDe(orden.getCliente()));
        datos.put("mensaje", mensaje);
        datos.put("estado", orden.getEstado().name());
        return datos;
    }

    private Map<String, Object> datosDispatch(WorkOrderEntity orden) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("ordenId", orden.getId());
        datos.put("cuadrilla", orden.getTecnicoAsignado());
        datos.put("direccion", orden.getDireccion());
        datos.put("descripcion", orden.getDescripcion());
        return datos;
    }

    private Map<String, Object> datosReport(WorkOrderEntity orden, String documento) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("ordenId", orden.getId());
        datos.put("documento", documento);
        datos.put("cliente", orden.getCliente());
        return datos;
    }

    private String correoDe(String cliente) {
        return cliente.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.|\\.$", "")
                + "@clientes.digitalfix.cl";
    }

    private String nuevoTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
