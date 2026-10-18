package cl.duoc.ms_digitalfix_workorders.dto;

import java.time.LocalDateTime;

public record WorkOrder(
        String id,
        String cliente,
        String descripcion,
        String direccion,
        EstadoOT estado,
        String tecnicoAsignado,
        String creadaPor,
        LocalDateTime creadaEn,
        LocalDateTime actualizadaEn) {
}
