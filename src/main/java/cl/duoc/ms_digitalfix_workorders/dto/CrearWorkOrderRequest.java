package cl.duoc.ms_digitalfix_workorders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearWorkOrderRequest(
        @NotBlank(message = "el cliente es obligatorio")
        String cliente,

        @NotBlank(message = "la descripcion es obligatoria")
        @Size(max = 500, message = "la descripcion no puede superar 500 caracteres")
        String descripcion,

        @NotBlank(message = "la direccion es obligatoria")
        String direccion) {
}
