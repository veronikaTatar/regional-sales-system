package by.regsales.repository;

import by.regsales.domain.RegionalStatistic;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RegionalStatisticRepository extends JpaRepository<RegionalStatistic, Long> {
    @EntityGraph(attributePaths = {"region", "indicator"})
    List<RegionalStatistic> findByIndicatorCodeInAndPeriodEndLessThanEqualAndStatisticsImportSourceNameOrderByPeriodEndDesc(
            Collection<String> codes, LocalDate cutoff, String sourceName);

    @EntityGraph(attributePaths = {"region", "indicator"})
    List<RegionalStatistic> findByIndicatorCodeInAndPeriodStartAndPeriodEnd(
            Collection<String> codes, LocalDate start, LocalDate end);
}
