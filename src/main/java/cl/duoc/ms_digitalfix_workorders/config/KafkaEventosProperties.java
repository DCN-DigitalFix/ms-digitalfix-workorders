package cl.duoc.ms_digitalfix_workorders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topico de salida y bandera de encendido, leidos desde application.yaml bajo
 * "digitalfix.kafka". Igual razon que "habilitada" en MensajeriaProperties:
 * permite levantar el servicio sin Kafka para trabajar en el CRUD.
 */
@ConfigurationProperties(prefix = "digitalfix.kafka")
public record KafkaEventosProperties(boolean habilitada, Topics topics) {

    public record Topics(String workordersEvents) {}
}
