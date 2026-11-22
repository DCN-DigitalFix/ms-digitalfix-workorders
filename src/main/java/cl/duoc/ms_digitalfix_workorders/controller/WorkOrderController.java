package cl.duoc.ms_digitalfix_workorders.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.ms_digitalfix_workorders.dto.CambiarEstadoRequest;
import cl.duoc.ms_digitalfix_workorders.dto.CrearWorkOrderRequest;
import cl.duoc.ms_digitalfix_workorders.dto.WorkOrder;
import cl.duoc.ms_digitalfix_workorders.service.WorkOrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/workorders")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Supervisor', 'Cliente', 'Auditor') and hasAuthority('SCOPE_OT.Read')")
    public ResponseEntity<List<WorkOrder>> listar(@AuthenticationPrincipal Jwt jwt) {
        boolean esSoloCliente = tieneRol(jwt, "Cliente")
                && !tieneRol(jwt, "Admin")
                && !tieneRol(jwt, "Supervisor")
                && !tieneRol(jwt, "Auditor");

        List<WorkOrder> resultado = esSoloCliente
                ? service.listarPorCreador(jwt.getSubject())
                : service.listar();

        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Supervisor', 'Cliente', 'Auditor') and hasAuthority('SCOPE_OT.Read')")
    public ResponseEntity<WorkOrder> obtener(@PathVariable String id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('Admin', 'Supervisor', 'Cliente') and hasAuthority('SCOPE_OT.Create')")
    public ResponseEntity<WorkOrder> crear(
            @Valid @RequestBody CrearWorkOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        WorkOrder creada = service.crear(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('Admin', 'Supervisor') and hasAuthority('SCOPE_OT.Create')")
    public ResponseEntity<WorkOrder> cambiarEstado(
            @PathVariable String id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return ResponseEntity.ok(service.cambiarEstado(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin') and hasAuthority('SCOPE_OT.Create')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private boolean tieneRol(Jwt jwt, String rol) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(rol);
    }
}
