package cl.duoc.ms_digitalfix_workorders.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Verifica el JSON que produce el mismo ObjectMapper (Jackson + JavaTimeModule,
 * timestamps como texto ISO-8601) que configura KafkaProducerConfig, sin
 * levantar el productor real.
 */
class WorkOrderEventSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void elJsonIncluyeLosDiezCamposDelContrato() throws Exception {
        WorkOrderEvent evento = new WorkOrderEvent(
                "e-1",
                WorkOrderEvent.ORDEN_CREADA,
                Instant.parse("2026-11-22T10:00:00Z"),
                "trace-1",
                "OT-0001",
                null,
                "INGRESADA",
                "Cliente Prueba",
                null,
                "sistema");

        String json = mapper.writeValueAsString(evento);

        assertThat(json)
                .contains("\"eventId\":\"e-1\"")
                .contains("\"eventType\":\"ORDEN_CREADA\"")
                .contains("\"traceId\":\"trace-1\"")
                .contains("\"ordenId\":\"OT-0001\"")
                .contains("\"estadoAnterior\":null")
                .contains("\"estadoNuevo\":\"INGRESADA\"")
                .contains("\"cliente\":\"Cliente Prueba\"")
                .contains("\"tecnicoAsignado\":null")
                .contains("\"actor\":\"sistema\"");
    }

    @Test
    void occurredAtSeSerializaComoIso8601() throws Exception {
        WorkOrderEvent evento = new WorkOrderEvent(
                "e-1", WorkOrderEvent.ORDEN_CREADA, Instant.parse("2026-11-22T10:00:00Z"),
                "trace-1", "OT-0001", null, "INGRESADA", "Cliente Prueba", null, "sistema");

        String json = mapper.writeValueAsString(evento);

        // Sin JavaTimeModule, Instant se serializa como un arreglo [segundos, nanos];
        // con el modulo, como texto ISO-8601. Si esto falla, es el primer caso.
        assertThat(json).contains("\"occurredAt\":\"2026-11-22T10:00:00Z\"");
    }

    @Test
    void elJsonSeDeserializaDeVueltaAlMismoEvento() throws Exception {
        WorkOrderEvent original = new WorkOrderEvent(
                "e-2", WorkOrderEvent.ESTADO_CAMBIADO, Instant.parse("2026-11-22T11:30:00Z"),
                "trace-2", "OT-0002", "INGRESADA", "ASIGNADA", "Cliente Dos", "Tecnico Uno", "user-123");

        String json = mapper.writeValueAsString(original);
        WorkOrderEvent reconstruido = mapper.readValue(json, WorkOrderEvent.class);

        assertThat(reconstruido).isEqualTo(original);
    }
}
