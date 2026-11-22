package cl.duoc.ms_digitalfix_workorders.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import cl.duoc.ms_digitalfix_workorders.config.KafkaEventosProperties;
import cl.duoc.ms_digitalfix_workorders.dto.EstadoOT;
import cl.duoc.ms_digitalfix_workorders.entity.WorkOrderEntity;
import cl.duoc.ms_digitalfix_workorders.messaging.kafka.WorkOrderEvent;

@ExtendWith(MockitoExtension.class)
class WorkOrderKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, WorkOrderEvent> kafkaTemplate;

    private final KafkaEventosProperties props =
            new KafkaEventosProperties(true, new KafkaEventosProperties.Topics("digitalfix.workorders.events"));

    private final WorkOrderEntity orden = new WorkOrderEntity(
            "OT-0001", "Cliente Prueba", "Descripcion", "Direccion",
            EstadoOT.INGRESADA, null, "sistema", LocalDateTime.now(), LocalDateTime.now());

    @Test
    void publicaConLaOrdenIdComoClaveYElTopicoConfigurado() {
        // El futuro se arma ANTES del when(...): crear el mock de SendResult
        // dentro del argumento de thenReturn() deja la stubbing "a medias"
        // para Mockito (UnfinishedStubbingException), porque mock(...) y
        // when(...) de otro mock se ejecutan mientras la primera stubbing
        // todavia esta en curso.
        CompletableFuture<SendResult<String, WorkOrderEvent>> futuro = futuroExitoso();
        when(kafkaTemplate.send(eq("digitalfix.workorders.events"), eq("OT-0001"), any()))
                .thenReturn(futuro);

        WorkOrderKafkaPublisher publisher = new WorkOrderKafkaPublisher(kafkaTemplate, props);
        publisher.publicarCreada(orden);

        ArgumentCaptor<WorkOrderEvent> captor = ArgumentCaptor.forClass(WorkOrderEvent.class);
        verify(kafkaTemplate).send(eq("digitalfix.workorders.events"), eq("OT-0001"), captor.capture());

        WorkOrderEvent evento = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(evento.eventType()).isEqualTo(WorkOrderEvent.ORDEN_CREADA);
        org.assertj.core.api.Assertions.assertThat(evento.ordenId()).isEqualTo("OT-0001");
        org.assertj.core.api.Assertions.assertThat(evento.estadoAnterior()).isNull();
        org.assertj.core.api.Assertions.assertThat(evento.estadoNuevo()).isEqualTo("INGRESADA");
    }

    @Test
    void unCambioDeEstadoIncluyeElEstadoAnterior() {
        CompletableFuture<SendResult<String, WorkOrderEvent>> futuro = futuroExitoso();
        when(kafkaTemplate.send(eq("digitalfix.workorders.events"), eq("OT-0001"), any()))
                .thenReturn(futuro);

        orden.setEstado(EstadoOT.ASIGNADA);
        WorkOrderKafkaPublisher publisher = new WorkOrderKafkaPublisher(kafkaTemplate, props);
        publisher.publicarCambioEstado(orden, "INGRESADA");

        ArgumentCaptor<WorkOrderEvent> captor = ArgumentCaptor.forClass(WorkOrderEvent.class);
        verify(kafkaTemplate).send(eq("digitalfix.workorders.events"), eq("OT-0001"), captor.capture());

        WorkOrderEvent evento = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(evento.eventType()).isEqualTo(WorkOrderEvent.ESTADO_CAMBIADO);
        org.assertj.core.api.Assertions.assertThat(evento.estadoAnterior()).isEqualTo("INGRESADA");
        org.assertj.core.api.Assertions.assertThat(evento.estadoNuevo()).isEqualTo("ASIGNADA");
    }

    @Test
    void unFalloDelBrokerNoPropagaExcepcionHaciaElFlujoDeNegocio() {
        CompletableFuture<SendResult<String, WorkOrderEvent>> futuroFallido = new CompletableFuture<>();
        futuroFallido.completeExceptionally(new RuntimeException("el cluster no respondio"));

        when(kafkaTemplate.send(eq("digitalfix.workorders.events"), eq("OT-0001"), any()))
                .thenReturn(futuroFallido);

        WorkOrderKafkaPublisher publisher = new WorkOrderKafkaPublisher(kafkaTemplate, props);

        assertThatCode(() -> publisher.publicarCreada(orden)).doesNotThrowAnyException();
    }

    @Test
    void conKafkaDeshabilitadoNoLlamaAlTemplate() {
        KafkaEventosProperties desactivado =
                new KafkaEventosProperties(false, new KafkaEventosProperties.Topics("digitalfix.workorders.events"));
        WorkOrderKafkaPublisher publisher = new WorkOrderKafkaPublisher(kafkaTemplate, desactivado);

        publisher.publicarCreada(orden);

        verifyNoInteractions(kafkaTemplate);
    }

    private CompletableFuture<SendResult<String, WorkOrderEvent>> futuroExitoso() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("digitalfix.workorders.events", 0), 0, 0, 0, 0, 0);
        SendResult<String, WorkOrderEvent> resultado = mock(SendResult.class);
        when(resultado.getRecordMetadata()).thenReturn(metadata);
        return CompletableFuture.completedFuture(resultado);
    }
}
