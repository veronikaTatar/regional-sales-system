package by.regsales.repository;

import by.regsales.domain.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {
    @Override
    @EntityGraph(attributePaths = {"product", "region"})
    Page<Shipment> findAll(Specification<Shipment> specification, Pageable pageable);

    Optional<Shipment> findByReferenceNumberIgnoreCase(String referenceNumber);

    @Query("""
            select shipment.product.id as productId, sum(shipment.quantity) as quantity, sum(shipment.totalAmount) as revenue
            from Shipment shipment where shipment.region.id = :regionId and shipment.shipmentDate between :from and :to
            group by shipment.product.id
            """)
    List<ProductTotal> factByProduct(Long regionId, LocalDate from, LocalDate to);

    @Query("""
            select plan.id as planId, sum(shipment.totalAmount) as revenue
            from RegionalPlan plan join Shipment shipment on shipment.region.id = plan.region.id
                and shipment.shipmentDate between plan.periodStart and plan.periodEnd
            where plan.id in :planIds group by plan.id
            """)
    List<PlanTotal> factByPlan(Collection<Long> planIds);

    interface PlanTotal {
        Long getPlanId();
        BigDecimal getRevenue();
    }

    interface ProductTotal {
        Long getProductId();
        BigDecimal getQuantity();
        BigDecimal getRevenue();
    }
}
