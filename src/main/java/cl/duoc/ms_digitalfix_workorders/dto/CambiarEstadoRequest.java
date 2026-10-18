package cl.duoc.ms_digitalfix_workorders.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(
        @NotNull(message = "el estado es obligatorio")
        EstadoOT estado,

        String tecnicoAsignado) {
}
