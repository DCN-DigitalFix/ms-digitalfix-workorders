package cl.duoc.ms_digitalfix_workorders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cl.duoc.ms_digitalfix_workorders.dto.CambiarEstadoRequest;
import cl.duoc.ms_digitalfix_workorders.dto.EstadoOT;
import cl.duoc.ms_digitalfix_workorders.dto.WorkOrder;
import cl.duoc.ms_digitalfix_workorders.entity.WorkOrderEntity;
import cl.duoc.ms_digitalfix_workorders.exception.EstadoInvalidoException;
import cl.duoc.ms_digitalfix_workorders.exception.StockInsuficienteException;
import cl.duoc.ms_digitalfix_workorders.repository.WorkOrderRepository;

/**
 * Cubre el descuento de stock al asignar (ver pendientes-y-versionado.md,
 * Parte 2): es la unica regla de negocio nueva de este cambio, el resto del
 * servicio (listar, crear, eliminar) no se toco.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository repository;
    @Mock
    private WorkOrderEventPublisher publisher;
    @Mock
    private WorkOrderKafkaPublisher kafkaPublisher;
    @Mock
    private CatalogClient catalogClient;

    private WorkOrderService service;

    @BeforeEach
    void setUp() {
        // No puede ser un inicializador de campo: los @Mock se inyectan
        // despues de que corren los inicializadores, asi que todo quedaria
        // construido con mocks null. WorkOrderService se inyecta por campo,
        // asi que tampoco hay constructor que invocar: se arma con
        // ReflectionTestUtils.
        service = new WorkOrderService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "publisher", publisher);
        ReflectionTestUtils.setField(service, "kafkaPublisher", kafkaPublisher);
        ReflectionTestUtils.setField(service, "catalogClient", catalogClient);
    }

    private WorkOrderEntity ordenIngresada() {
        return new WorkOrderEntity("OT-0001", "Cliente Prueba", "Descripcion", "Direccion",
                EstadoOT.INGRESADA, null, "sistema", LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void asignarConRepuestoYStockSuficienteDescuentaYQuedaAsignada() {
        WorkOrderEntity orden = ordenIngresada();
        when(repository.findById("OT-0001")).thenReturn(Optional.of(orden));
        when(repository.save(any())).thenReturn(orden);

        CambiarEstadoRequest request = new CambiarEstadoRequest(
                EstadoOT.ASIGNADA, "Cuadrilla Norte", "AUT-16A", 2);

        WorkOrder resultado = service.cambiarEstado("OT-0001", request);

        verify(catalogClient).descontarStock("AUT-16A", 2);
        assertThat(resultado.estado()).isEqualTo(EstadoOT.ASIGNADA);
        assertThat(resultado.codigoRepuesto()).isEqualTo("AUT-16A");
        assertThat(resultado.cantidadRepuesto()).isEqualTo(2);
        verify(repository).save(orden);
    }

    @Test
    void asignarSinStockSuficienteNoDejaLaOrdenAsignada() {
        WorkOrderEntity orden = ordenIngresada();
        when(repository.findById("OT-0001")).thenReturn(Optional.of(orden));
        doThrow(new StockInsuficienteException("No hay stock suficiente del repuesto AUT-16A"))
                .when(catalogClient).descontarStock("AUT-16A", 5);

        CambiarEstadoRequest request = new CambiarEstadoRequest(
                EstadoOT.ASIGNADA, "Cuadrilla Norte", "AUT-16A", 5);

        assertThatThrownBy(() -> service.cambiarEstado("OT-0001", request))
                .isInstanceOf(StockInsuficienteException.class);

        // Lo que importa: la orden no quedo asignada ni se publico nada, no
        // solo que la excepcion se propago.
        assertThat(orden.getEstado()).isEqualTo(EstadoOT.INGRESADA);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher, kafkaPublisher);
    }

    @Test
    void asignarSinCodigoRepuestoNoConsultaCatalog() {
        WorkOrderEntity orden = ordenIngresada();
        when(repository.findById("OT-0001")).thenReturn(Optional.of(orden));
        when(repository.save(any())).thenReturn(orden);

        CambiarEstadoRequest request = new CambiarEstadoRequest(
                EstadoOT.ASIGNADA, "Cuadrilla Norte", null, null);

        WorkOrder resultado = service.cambiarEstado("OT-0001", request);

        verifyNoInteractions(catalogClient);
        assertThat(resultado.estado()).isEqualTo(EstadoOT.ASIGNADA);
        assertThat(resultado.codigoRepuesto()).isNull();
    }
    
    @Test
    void asignarConCodigoSinCantidadFallaYNoConsultaCatalog() {
        WorkOrderEntity orden = ordenIngresada();
        when(repository.findById("OT-0001")).thenReturn(Optional.of(orden));

        // @Positive deja pasar los null, asi que esta peticion atraviesa la
        // validacion y llega hasta el servicio: la guarda tiene que estar
        // aqui y no en el DTO.
        CambiarEstadoRequest request = new CambiarEstadoRequest(
                EstadoOT.ASIGNADA, "Cuadrilla Norte", "AUT-16A", null);

        assertThatThrownBy(() -> service.cambiarEstado("OT-0001", request))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("AUT-16A");

        // Lo que de verdad verifica esta prueba: que el rechazo no deje
        // rastro. Si la guarda se moviera despues del descontarStock, el 400
        // seguiria apareciendo y el stock ya estaria descontado.
        verifyNoInteractions(catalogClient);
        assertThat(orden.getEstado()).isEqualTo(EstadoOT.INGRESADA);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher, kafkaPublisher);
    }
}
