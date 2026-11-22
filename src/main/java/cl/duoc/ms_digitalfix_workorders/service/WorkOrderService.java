package cl.duoc.ms_digitalfix_workorders.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cl.duoc.ms_digitalfix_workorders.dto.CambiarEstadoRequest;
import cl.duoc.ms_digitalfix_workorders.dto.CrearWorkOrderRequest;
import cl.duoc.ms_digitalfix_workorders.dto.EstadoOT;
import cl.duoc.ms_digitalfix_workorders.dto.WorkOrder;
import cl.duoc.ms_digitalfix_workorders.entity.WorkOrderEntity;
import cl.duoc.ms_digitalfix_workorders.exception.EstadoInvalidoException;
import cl.duoc.ms_digitalfix_workorders.exception.NotFoundException;
import cl.duoc.ms_digitalfix_workorders.repository.WorkOrderRepository;

import jakarta.annotation.PostConstruct;

@Service
public class WorkOrderService {

    private static final Map<EstadoOT, Set<EstadoOT>> TRANSICIONES = Map.of(
            EstadoOT.INGRESADA, Set.of(EstadoOT.ASIGNADA, EstadoOT.ANULADA),
            EstadoOT.ASIGNADA, Set.of(EstadoOT.EN_EJECUCION, EstadoOT.ANULADA),
            EstadoOT.EN_EJECUCION, Set.of(EstadoOT.CERRADA, EstadoOT.ANULADA),
            EstadoOT.CERRADA, Set.of(),
            EstadoOT.ANULADA, Set.of());

    private final WorkOrderRepository repository;
    private final WorkOrderEventPublisher publisher;
    private final WorkOrderKafkaPublisher kafkaPublisher;

    @Autowired
    public WorkOrderService(
            WorkOrderRepository repository,
            WorkOrderEventPublisher publisher,
            WorkOrderKafkaPublisher kafkaPublisher) {
        this.repository = repository;
        this.publisher = publisher;
        this.kafkaPublisher = kafkaPublisher;
    }

    @PostConstruct
    void seed() {
        if (repository.count() == 0) {
            LocalDateTime ahora = LocalDateTime.now();
            repository.save(new WorkOrderEntity("OT-0001", "Condominio Los Aromos",
                    "Tablero general con corte intermitente en el bloque B",
                    "Av. Los Aromos 1420, Lampa", EstadoOT.ASIGNADA, "Cuadrilla Norte",
                    "sistema", ahora.minusDays(2), ahora.minusDays(1)));
            repository.save(new WorkOrderEntity("OT-0002", "Panaderia San Miguel",
                    "Recambio de automatico y revision de empalme",
                    "Calle Principal 87, Colina", EstadoOT.INGRESADA, null,
                    "sistema", ahora.minusDays(1), ahora.minusDays(1)));
        }
    }

    public List<WorkOrder> listar() {
        return repository.findAll().stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(WorkOrder::creadaEn).reversed())
                .toList();
    }

    public List<WorkOrder> listarPorCreador(String creador) {
        return repository.findAll().stream()
                .filter(e -> e.getCreadaPor().equals(creador))
                .map(this::toDto)
                .sorted(Comparator.comparing(WorkOrder::creadaEn).reversed())
                .toList();
    }

    public WorkOrder obtener(String id) {
        return toDto(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la orden de trabajo " + id)));
    }

    public WorkOrder crear(CrearWorkOrderRequest request, String creador) {
        String id = siguienteId();
        LocalDateTime ahora = LocalDateTime.now();

        WorkOrderEntity entity = new WorkOrderEntity(
                id,
                request.cliente(),
                request.descripcion(),
                request.direccion(),
                EstadoOT.INGRESADA,
                null,
                creador,
                ahora,
                ahora);

        repository.save(entity);

        // Ya esta guardada: recien ahora tiene sentido avisar. Cada publisher
        // registra sus propios fallos, asi que un broker caido (RabbitMQ o
        // Kafka) no rompe esto.
        publisher.publicarCreada(entity);
        kafkaPublisher.publicarCreada(entity);

        return toDto(entity);
    }

    public WorkOrder cambiarEstado(String id, CambiarEstadoRequest request) {
        WorkOrderEntity actual = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la orden de trabajo " + id));

        // Se captura antes de mutar la entidad: Kafka necesita el estado
        // anterior en el evento y despues de setEstado() ya no esta disponible.
        EstadoOT estadoAnterior = actual.getEstado();

        if (!TRANSICIONES.getOrDefault(estadoAnterior, Set.of()).contains(request.estado())) {
            throw new EstadoInvalidoException(
                    "Transicion invalida de " + estadoAnterior + " a " + request.estado());
        }

        actual.setEstado(request.estado());
        if (request.tecnicoAsignado() != null) {
            actual.setTecnicoAsignado(request.tecnicoAsignado());
        }
        actual.setActualizadaEn(LocalDateTime.now());

        repository.save(actual);

        // Que flujos se disparan lo decide el publisher: este servicio solo
        // informa el hecho y no conoce a los consumidores.
        // Se usa if/else y no switch a proposito: un switch sobre enum obliga
        // al compilador a generar una clase sintetica auxiliar con el mapa de
        // constantes, y basta con que esa clase no llegue a target/classes
        // para que el metodo falle en ejecucion con NoClassDefFoundError.
        EstadoOT nuevoEstado = request.estado();

        if (nuevoEstado == EstadoOT.ASIGNADA) {
            publisher.publicarAsignada(actual);
        } else if (nuevoEstado == EstadoOT.CERRADA) {
            publisher.publicarCerrada(actual);
        } else if (nuevoEstado == EstadoOT.ANULADA) {
            publisher.publicarAnulada(actual);
        }
        // INGRESADA y EN_EJECUCION no emiten eventos propios hacia RabbitMQ.

        // Kafka si registra cada transicion, sin excepcion: es el historial
        // completo que consume audit y la foto de estado que arma report.
        kafkaPublisher.publicarCambioEstado(actual, estadoAnterior.name());

        return toDto(actual);
    }

    public void eliminar(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("No existe la orden de trabajo " + id);
        }
        repository.deleteById(id);
    }

    private String siguienteId() {
        long count = repository.count();
        return "OT-%04d".formatted(count + 1);
    }

    private WorkOrder toDto(WorkOrderEntity e) {
        return new WorkOrder(
                e.getId(),
                e.getCliente(),
                e.getDescripcion(),
                e.getDireccion(),
                e.getEstado(),
                e.getTecnicoAsignado(),
                e.getCreadaPor(),
                e.getCreadaEn(),
                e.getActualizadaEn());
    }
}
