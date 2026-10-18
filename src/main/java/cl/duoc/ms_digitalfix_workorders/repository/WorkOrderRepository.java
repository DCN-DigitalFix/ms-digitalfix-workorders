package cl.duoc.ms_digitalfix_workorders.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.ms_digitalfix_workorders.entity.WorkOrderEntity;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, String> {
}
