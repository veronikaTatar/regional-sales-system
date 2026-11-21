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
            select shipment.product.id as productId, sum(shipment.quantity) as quantity,
                sum(shipment.totalAmount) as revenue, count(shipment.id) as shipmentCount
            from Shipment shipment where shipment.region.id = :regionId and shipment.shipmentDate between :from and :to
            group by shipment.product.id
            """)
    List<ProductTotal> factByProduct(Long regionId, LocalDate from, LocalDate to);

    @Query("""
            select year(s.shipmentDate) as shipmentYear, month(s.shipmentDate) as shipmentMonth,
                s.region.id as regionId, s.product.id as productId, sum(s.quantity) as quantity,
                sum(s.totalAmount) as revenue, count(s.id) as shipmentCount
            from Shipment s
            where (:regionId is null or s.region.id = :regionId) and (:productId is null or s.product.id = :productId)
                and s.shipmentDate between :from and :to
            group by year(s.shipmentDate), month(s.shipmentDate), s.region.id, s.product.id
            """)
    List<MonthlyTotal> summarize(Long regionId, Long productId, LocalDate from, LocalDate to);

    interface MonthlyTotal extends ProductTotal {
        int getShipmentYear();
        int getShipmentMonth();
        Long getRegionId();
    }

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
        long getShipmentCount();
    }
}
