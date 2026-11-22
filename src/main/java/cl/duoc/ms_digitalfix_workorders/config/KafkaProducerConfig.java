package cl.duoc.ms_digitalfix_workorders.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import cl.duoc.ms_digitalfix_workorders.messaging.kafka.WorkOrderEvent;

/**
 * Configuracion del lado productor de Kafka.
 *
 * acks=all + enable.idempotence=true: el productor espera la confirmacion de
 * todas las replicas sincronas (min.insync.replicas=2 en el topico) y Kafka
 * descarta reintentos duplicados del mismo productor. max.block.ms bajo para
 * que, si el cluster no responde, publicar() no bloquee el hilo HTTP por
 * mucho tiempo: el error se resuelve de forma asincrona en el publisher.
 *
 * El serializador de valor es JSON con JavaTimeModule (para Instant) y sin
 * las cabeceras de tipo de Spring Kafka (noTypeInfo()): audit y report leen
 * el mensaje como texto plano con su propio parser, no con JsonDeserializer,
 * asi que esas cabeceras no aportan nada y conviene no agregarlas.
 *
 * JsonSerializer (Jackson 2, com.fasterxml.jackson) esta deprecado desde
 * Spring Kafka 4.0 a favor de JacksonJsonSerializer, que usa Jackson 3
 * (tools.jackson.databind), un artefacto distinto del que usa el resto de
 * este proyecto. Se mantiene el deprecado a proposito, por consistencia con
 * el ObjectMapper de com.fasterxml.jackson que ya usan audit, report y el BFF.
 */
@Configuration
@EnableConfigurationProperties(KafkaEventosProperties.class)
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, WorkOrderEvent> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);

        // Sin deshabilitar WRITE_DATES_AS_TIMESTAMPS, Instant se serializa
        // como [segundos, nanos] en vez de texto ISO-8601, que es el formato
        // que esperan audit y report al parsear "occurredAt".
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JsonSerializer<WorkOrderEvent> valueSerializer = new JsonSerializer<WorkOrderEvent>(mapper).noTypeInfo();

        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, WorkOrderEvent> kafkaTemplate(
            ProducerFactory<String, WorkOrderEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
