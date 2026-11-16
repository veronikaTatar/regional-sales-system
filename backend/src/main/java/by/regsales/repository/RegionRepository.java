package by.regsales.repository;

import by.regsales.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Long> {
    List<Region> findAllByActiveTrueOrderByNameAsc();
}
