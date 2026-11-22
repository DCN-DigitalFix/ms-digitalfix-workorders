package cl.duoc.ms_digitalfix_workorders.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CambiarEstadoRequest(
        @NotNull(message = "el estado es obligatorio")
        EstadoOT estado,

        String tecnicoAsignado,

        // Los dos opcionales a proposito: no toda orden consume repuesto (p.
        // ej. una visita de diagnostico). Si viene codigoRepuesto, se
        // descuenta del catalogo al asignar; si no, se asigna sin mas.
        String codigoRepuesto,

        @Positive(message = "la cantidad debe ser mayor que cero")
        Integer cantidadRepuesto) {
}
