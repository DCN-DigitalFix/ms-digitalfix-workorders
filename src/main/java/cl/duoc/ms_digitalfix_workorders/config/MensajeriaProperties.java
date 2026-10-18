package cl.duoc.ms_digitalfix_workorders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Destinos de publicacion, leidos desde application.yaml bajo
 * "digitalfix.mensajeria".
 *
 * El BFF solo produce: no declara colas ni las consume. Por eso aqui apenas
 * necesita los exchanges y las routing keys, mientras que
 * ms-digitalfix-notify, que es quien consume, tiene la topologia completa.
 *
 * Igual que alla, ninguna clase escribe estos nombres a mano.
 */
@ConfigurationProperties(prefix = "digitalfix.mensajeria")
public record MensajeriaProperties(
        boolean habilitada,
        Exchanges exchanges,
        RoutingKeys routingKeys) {

    public record Exchanges(String direct, String topic) {}

    /**
     * @param email    notificacion al cliente
     * @param dispatch ticket de despacho a la cuadrilla
     * @param report   generacion de documento
     */
    public record RoutingKeys(String email, String dispatch, String report) {}
}
