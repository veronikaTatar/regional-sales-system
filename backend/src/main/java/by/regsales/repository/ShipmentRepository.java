package by.regsales.repository;

import by.regsales.domain.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {
    @Override
    @EntityGraph(attributePaths = {"product", "region"})
    Page<Shipment> findAll(Specification<Shipment> specification, Pageable pageable);

    Optional<Shipment> findByReferenceNumberIgnoreCase(String referenceNumber);
}
