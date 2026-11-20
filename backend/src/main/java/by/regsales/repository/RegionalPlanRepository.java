package by.regsales.repository;

import by.regsales.domain.RegionalPlan;
import by.regsales.domain.PlanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegionalPlanRepository extends JpaRepository<RegionalPlan, Long> {
    @EntityGraph(attributePaths = {"region", "assignedManager"})
    List<RegionalPlan> findAllByOrderByPeriodStartDescIdDesc();

    @EntityGraph(attributePaths = {"region", "assignedManager"})
    List<RegionalPlan> findByStatusOrderByPeriodStartDescIdDesc(PlanStatus status);

    @EntityGraph(attributePaths = {"region", "analyticalRecommendation.authorUser", "analyticalRecommendation.purchasingPowerCalculation"})
    List<RegionalPlan> findByAnalyticalRecommendationIsNotNullOrderByPeriodStartDescIdDesc();

    @EntityGraph(attributePaths = {"region", "assignedManager"})
    List<RegionalPlan> findByAssignedManagerIdAndRegionIdInAndSentToManagerAtIsNotNullOrderByPeriodStartDescIdDesc(
            Long managerId, Collection<Long> regionIds);

    Optional<RegionalPlan> findByRegionIdAndPeriodStartAndPeriodEnd(Long regionId, LocalDate from, LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from RegionalPlan plan where plan.id = :id")
    Optional<RegionalPlan> findForUpdate(Long id);
}
