package cl.duoc.ms_digitalfix_workorders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Configuracion del lado productor.
 *
 * Este microservicio publica, no consume. Las colas, las DLQ y sus bindings
 * los declara ms-digitalfix-notify, que es quien las lee: la topologia
 * pertenece a quien la consume.
 *
 * Los dos exchanges si se declaran aqui, porque publicar hacia uno que no
 * existe falla. Declarar un exchange es idempotente, asi que que ambos
 * servicios lo hagan no causa conflicto y permite que workorders funcione
 * aunque notify todavia no haya arrancado.
 *
 * Los callbacks de confirmacion no son un adorno: sin ellos, un mensaje
 * enviado a un exchange inexistente o con una routing key que no enlaza con
 * ninguna cola se descarta en silencio y desaparece sin dejar rastro.
 */
@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class RabbitProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitProducerConfig.class);

    @Autowired
    private MensajeriaProperties props;

    /** Comandos con destinatario unico y conocido. */
    @Bean
    public DirectExchange cmdDirectExchange() {
        return new DirectExchange(props.exchanges().direct(), true, false);
    }

    /** Comandos enrutados por patron: email.*, dispatch.#, report.* */
    @Bean
    public TopicExchange cmdTopicExchange() {
        return new TopicExchange(props.exchanges().topic(), true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);

        // El broker acepto o rechazo el mensaje.
        // Requiere publisher-confirm-type: correlated en application.yaml.
        template.setConfirmCallback((correlationData, confirmado, causa) -> {
            if (!confirmado) {
                log.error("El broker rechazo un mensaje: {}", causa);
            }
        });

        // El exchange existia pero la routing key no enlaza con ninguna cola.
        // Requiere publisher-returns: true en application.yaml.
        template.setMandatory(true);
        template.setReturnsCallback(returned ->
                log.error("Mensaje sin cola de destino. Exchange '{}', routing key '{}': {}",
                        returned.getExchange(),
                        returned.getRoutingKey(),
                        returned.getReplyText()));

        return template;
    }
}
