package cl.duoc.ms_digitalfix_workorders.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import cl.duoc.ms_digitalfix_workorders.exception.CatalogNoDisponibleException;
import cl.duoc.ms_digitalfix_workorders.exception.NotFoundException;
import cl.duoc.ms_digitalfix_workorders.exception.StockInsuficienteException;

@ExtendWith(MockitoExtension.class)
class CatalogClientTest {

    private static final String BASE_URL = "http://localhost:8082";
    private static final String URL = BASE_URL + "/api/v1/catalog/repuestos/AUT-16A/descontar";

    @Mock
    private RestTemplate restTemplate;

    /** CatalogClient se inyecta por campo: no tiene constructor que invocar. */
    private CatalogClient client() {
        CatalogClient catalogClient = new CatalogClient();
        ReflectionTestUtils.setField(catalogClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(catalogClient, "baseUrl", BASE_URL);
        return catalogClient;
    }

    @Test
    void descuentaElStockConLaCantidadPedida() {
        CatalogClient catalogClient = client();

        catalogClient.descontarStock("AUT-16A", 2);

        verify(restTemplate).postForEntity(eq(URL), eq(Map.of("cantidad", 2)), eq(Void.class));
    }

    @Test
    void stockInsuficienteSeTraduceConElMensajeDeCatalog() {
        CatalogClient catalogClient = client();
        String cuerpo = "{\"message\":\"Stock insuficiente de AUT-16A: hay 2, se requieren 5\"}";
        // Conflict/NotFound tienen constructor privado: la unica forma de
        // obtener una instancia es el factory de la clase base, que elige el
        // subtipo segun el status code.
        HttpClientErrorException conflicto = HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", new HttpHeaders(),
                cuerpo.getBytes(), null);

        when(restTemplate.postForEntity(eq(URL), any(), eq(Void.class))).thenThrow(conflicto);

        assertThatThrownBy(() -> catalogClient.descontarStock("AUT-16A", 5))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessage("Stock insuficiente de AUT-16A: hay 2, se requieren 5");
    }

    @Test
    void repuestoInexistenteSeTraduceA404DeWorkorders() {
        CatalogClient catalogClient = client();
        HttpClientErrorException noEncontrado = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], null);

        when(restTemplate.postForEntity(eq(URL), any(), eq(Void.class))).thenThrow(noEncontrado);

        assertThatThrownBy(() -> catalogClient.descontarStock("AUT-16A", 1))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void catalogCaidoSeTraduceAServicioNoDisponible() {
        CatalogClient catalogClient = client();

        when(restTemplate.postForEntity(eq(URL), any(), eq(Void.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> catalogClient.descontarStock("AUT-16A", 1))
                .isInstanceOf(CatalogNoDisponibleException.class);
    }

    @Test
    void sinCampoMessageUsaElMensajePorDefecto() {
        CatalogClient catalogClient = client();
        HttpClientErrorException conflicto = HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", new HttpHeaders(), new byte[0], null);

        when(restTemplate.postForEntity(eq(URL), any(), eq(Void.class))).thenThrow(conflicto);

        assertThatThrownBy(() -> catalogClient.descontarStock("AUT-16A", 1))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessage("No hay stock suficiente del repuesto AUT-16A");
    }
}
