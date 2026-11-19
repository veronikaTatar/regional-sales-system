package by.regsales.repository;

import by.regsales.domain.Region;
import by.regsales.domain.RegionAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RegionAssignmentRepository extends JpaRepository<RegionAssignment, Long> {
    @Query("select distinct assignment.manager.id from RegionAssignment assignment")
    Set<Long> findAssignedManagerIds();

    @EntityGraph(attributePaths = "region")
    List<RegionAssignment> findAllByManagerIdOrderByStartDateDescIdDesc(Long managerId);

    Optional<RegionAssignment> findByIdAndManagerId(Long id, Long managerId);

    @Query("""
        select count(assignment) > 0 from RegionAssignment assignment
        where assignment.manager.id = :managerId and assignment.region.id = :regionId
          and (:excludedId is null or assignment.id <> :excludedId)
          and (:endDate is null or assignment.startDate <= :endDate)
          and (assignment.endDate is null or assignment.endDate >= :startDate)
        """)
    boolean hasOverlappingPeriod(@Param("managerId") Long managerId, @Param("regionId") Long regionId,
                                 @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                 @Param("excludedId") Long excludedId);

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
