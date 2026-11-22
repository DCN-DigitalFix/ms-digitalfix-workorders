package cl.duoc.ms_digitalfix_workorders.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import cl.duoc.ms_digitalfix_workorders.exception.CatalogNoDisponibleException;
import cl.duoc.ms_digitalfix_workorders.exception.NotFoundException;
import cl.duoc.ms_digitalfix_workorders.exception.StockInsuficienteException;

/**
 * Cliente hacia ms-digitalfix-catalog para descontar stock al asignar una
 * orden. Traduce los errores de catalog al lenguaje de workorders: quien
 * llamo pregunto por una orden, no por un repuesto, asi que los codigos de
 * catalog no pueden salir tal cual.
 */
@Service
public class CatalogClient {

    private static final Pattern CAMPO_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public CatalogClient(RestTemplate restTemplate,
                          @Value("${digitalfix.catalog.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void descontarStock(String codigo, int cantidad) {
        String url = baseUrl + "/api/v1/catalog/repuestos/" + codigo + "/descontar";

        try {
            restTemplate.postForEntity(url, Map.of("cantidad", cantidad), Void.class);

        } catch (HttpClientErrorException.Conflict e) {
            throw new StockInsuficienteException(
                    mensajeDe(e, "No hay stock suficiente del repuesto " + codigo));

        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("No existe el repuesto " + codigo + " en el catalogo");

        } catch (ResourceAccessException e) {
            // Distinto de los dos anteriores: catalog no contesto, no es que
            // haya dicho que no. No es culpa de quien llamo, asi que no es
            // 4xx, y ademas comunica que reintentar tiene sentido.
            throw new CatalogNoDisponibleException(
                    "El catalogo no esta disponible, no se puede verificar el stock");
        }
    }

    /** Catalog responde algo preciso; reemplazarlo por un texto generico le quita al Supervisor los numeros. */
    private String mensajeDe(HttpClientErrorException e, String mensajePorDefecto) {
        String cuerpo = e.getResponseBodyAsString();
        Matcher m = CAMPO_MESSAGE.matcher(cuerpo);
        return m.find() ? m.group(1) : mensajePorDefecto;
    }
}
