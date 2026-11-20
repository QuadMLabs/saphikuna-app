package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CommissionRepository extends JpaRepository<Commission, UUID>, JpaSpecificationExecutor<Commission> {

    /** Nombres de clientes ya usados (para sugerirlos al escribir uno nuevo). */
    @Query("select distinct c.clientName from Commission c order by c.clientName")
    List<String> findClientNames();

    /** Encargos sin entregar con fecha igual o anterior a la dada, los más urgentes primero. */
    List<Commission> findByStatusNotAndDueDateLessThanEqualOrderByDueDateAsc(String status, LocalDate date);

    long countByStatusNotAndDueDateLessThanEqual(String status, LocalDate date);

    long countByStatusNot(String status);

    /** Si se renombra una marca, sus encargos muestran el nombre nuevo. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Commission c set c.clientName = :name where c.collaborationId = :brandId")
    int renameClient(@org.springframework.data.repository.query.Param("brandId") UUID brandId,
            @org.springframework.data.repository.query.Param("name") String name);
}
