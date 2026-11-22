package cl.duoc.ms_digitalfix_workorders.entity;

import java.time.LocalDateTime;

import cl.duoc.ms_digitalfix_workorders.dto.EstadoOT;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_orders")
public class WorkOrderEntity {

    @Id
    @Column(name = "id", nullable = false, length = 20)
    private String id;

    @Column(name = "cliente", nullable = false, length = 120)
    private String cliente;

    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoOT estado;

    @Column(name = "tecnico_asignado", length = 120)
    private String tecnicoAsignado;

    @Column(name = "creada_por", nullable = false, length = 120)
    private String creadaPor;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private LocalDateTime actualizadaEn;

    // Nulos para ordenes que no consumen repuesto (p. ej. una visita de
    // diagnostico). Se completan al asignar, si la peticion trae un repuesto.
    @Column(name = "codigo_repuesto", length = 40)
    private String codigoRepuesto;

    @Column(name = "cantidad_repuesto")
    private Integer cantidadRepuesto;

    public WorkOrderEntity() {
    }

    public WorkOrderEntity(String id, String cliente, String descripcion, String direccion,
                           EstadoOT estado, String tecnicoAsignado, String creadaPor,
                           LocalDateTime creadaEn, LocalDateTime actualizadaEn) {
        this.id = id;
        this.cliente = cliente;
        this.descripcion = descripcion;
        this.direccion = direccion;
        this.estado = estado;
        this.tecnicoAsignado = tecnicoAsignado;
        this.creadaPor = creadaPor;
        this.creadaEn = creadaEn;
        this.actualizadaEn = actualizadaEn;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public EstadoOT getEstado() { return estado; }
    public void setEstado(EstadoOT estado) { this.estado = estado; }
    public String getTecnicoAsignado() { return tecnicoAsignado; }
    public void setTecnicoAsignado(String tecnicoAsignado) { this.tecnicoAsignado = tecnicoAsignado; }
    public String getCreadaPor() { return creadaPor; }
    public void setCreadaPor(String creadaPor) { this.creadaPor = creadaPor; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(LocalDateTime creadaEn) { this.creadaEn = creadaEn; }
    public LocalDateTime getActualizadaEn() { return actualizadaEn; }
    public void setActualizadaEn(LocalDateTime actualizadaEn) { this.actualizadaEn = actualizadaEn; }
    public String getCodigoRepuesto() { return codigoRepuesto; }
    public void setCodigoRepuesto(String codigoRepuesto) { this.codigoRepuesto = codigoRepuesto; }
    public Integer getCantidadRepuesto() { return cantidadRepuesto; }
    public void setCantidadRepuesto(Integer cantidadRepuesto) { this.cantidadRepuesto = cantidadRepuesto; }
}
