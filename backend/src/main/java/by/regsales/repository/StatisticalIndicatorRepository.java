package by.regsales.repository;

import by.regsales.domain.StatisticalIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatisticalIndicatorRepository extends JpaRepository<StatisticalIndicator, Long> {
    Optional<StatisticalIndicator> findByCode(String code);
}
