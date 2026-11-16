package by.regsales.repository;

import by.regsales.domain.Region;
import by.regsales.domain.RegionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface RegionAssignmentRepository extends JpaRepository<RegionAssignment, Long> {
    @Query("""
        select distinct assignment.region from RegionAssignment assignment
        where assignment.manager.id = :managerId and assignment.region.active = true
          and assignment.startDate <= :date
          and (assignment.endDate is null or assignment.endDate >= :date)
        order by assignment.region.name
        """)
    List<Region> findAccessibleRegions(@Param("managerId") Long managerId, @Param("date") LocalDate date);

    @Query("""
        select count(assignment) > 0 from RegionAssignment assignment
        where assignment.manager.id = :managerId and assignment.region.id = :regionId
          and assignment.region.active = true and assignment.startDate <= :date
          and (assignment.endDate is null or assignment.endDate >= :date)
        """)
    boolean hasActiveAssignment(@Param("managerId") Long managerId, @Param("regionId") Long regionId,
                                @Param("date") LocalDate date);
}
