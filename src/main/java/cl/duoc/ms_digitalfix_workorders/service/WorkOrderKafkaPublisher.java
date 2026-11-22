package cl.duoc.ms_digitalfix_workorders.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import cl.duoc.ms_digitalfix_workorders.config.KafkaEventosProperties;
import cl.duoc.ms_digitalfix_workorders.entity.WorkOrderEntity;
import cl.duoc.ms_digitalfix_workorders.messaging.kafka.WorkOrderEvent;

/**
 * Publica en digitalfix.workorders.events. Es el productor que faltaba: sin
 * el, ms-digitalfix-audit y ms-digitalfix-report no reciben ningun evento
 * real, por mas que ya sepan consumir el topico.
 *
 * Mismo principio que WorkOrderEventPublisher (RabbitMQ): la mensajeria es
 * secundaria respecto del CRUD. La orden ya quedo guardada cuando se llama a
 * este publisher, asi que un Kafka caido no le quita al usuario su 201; el
 * fallo solo se registra en el log via whenComplete, nunca se relanza.
 */
@Service
public class WorkOrderKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderKafkaPublisher.class);

    private final KafkaTemplate<String, WorkOrderEvent> kafkaTemplate;
    private final KafkaEventosProperties props;

    @Autowired
    public WorkOrderKafkaPublisher(
            KafkaTemplate<String, WorkOrderEvent> kafkaTemplate, KafkaEventosProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    public void publicarCreada(WorkOrderEntity orden) {
        publicar(WorkOrderEvent.ORDEN_CREADA, orden, null);
    }

    /** estadoAnterior debe capturarse ANTES de mutar la entidad: aqui ya no esta disponible. */
    public void publicarCambioEstado(WorkOrderEntity orden, String estadoAnterior) {
        publicar(WorkOrderEvent.ESTADO_CAMBIADO, orden, estadoAnterior);
    }

    private void publicar(String eventType, WorkOrderEntity orden, String estadoAnterior) {
        if (!props.habilitada()) {
            log.debug("Kafka desactivado, no se publica {} para la orden {}", eventType, orden.getId());
            return;
        }

        WorkOrderEvent evento = new WorkOrderEvent(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                nuevoTraceId(),
                orden.getId(),
                estadoAnterior,
                orden.getEstado().name(),
                orden.getCliente(),
                orden.getTecnicoAsignado(),
                actorActual());

        // Clave = ordenId: todos los eventos de una orden caen en la misma
        // particion y audit/report los leen en el orden en que ocurrieron.
        kafkaTemplate.send(props.topics().workordersEvents(), orden.getId(), evento)
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar {} para la orden {}: {}",
                                eventType, orden.getId(), error.getMessage());
                    } else {
                        log.info("Publicado {} para la orden {} en la particion {}",
                                eventType, orden.getId(), resultado.getRecordMetadata().partition());
                    }
                });
    }

    /** El actor viaja en el evento para que audit lo conserve; "sistema" cubre llamadas sin JWT (seed, tests). */
    private String actorActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        return "sistema";
    }

    private String nuevoTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
