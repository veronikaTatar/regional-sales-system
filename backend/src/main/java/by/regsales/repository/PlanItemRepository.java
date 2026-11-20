package by.regsales.repository;

import by.regsales.domain.PlanItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {
    @EntityGraph(attributePaths = "product")
    List<PlanItem> findByRegionalPlanIdOrderByProductNameAscIdAsc(Long planId);

    boolean existsByRegionalPlanId(Long planId);

    @Query("""
            select item.regionalPlan.id as planId, sum(item.plannedAmount) as revenue from PlanItem item
            where item.regionalPlan.id in :planIds group by item.regionalPlan.id
            """)
    List<RevenueTotal> revenueByPlan(Collection<Long> planIds);

    interface RevenueTotal {
        Long getPlanId();
        BigDecimal getRevenue();
    }
}
