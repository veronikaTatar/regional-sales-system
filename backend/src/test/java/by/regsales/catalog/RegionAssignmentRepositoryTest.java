package by.regsales.catalog;

import by.regsales.domain.*;
import by.regsales.repository.RegionAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RegionAssignmentRepositoryTest {
    @Autowired TestEntityManager entities;
    @Autowired RegionAssignmentRepository assignments;

    @Test
    void periodBoundariesAndActiveRegionAreEnforcedByQuery() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Role role = new Role();
        role.setCode(RoleCode.MANAGER);
        role.setName("Менеджер по продажам");
        entities.persist(role);
        AppUser manager = new AppUser();
        manager.setLogin("manager");
        manager.setEmail("manager@example.test");
        manager.setFullName("Менеджер");
        manager.setRole(role);
        entities.persist(manager);
        Region beginsToday = assignment(manager, "START", date, null, true);
        Region endsToday = assignment(manager, "END", date.minusDays(10), date, true);
        Region expired = assignment(manager, "EXPIRED", date.minusDays(10), date.minusDays(1), true);
        Region future = assignment(manager, "FUTURE", date.plusDays(1), null, true);
        Region inactive = assignment(manager, "INACTIVE", date.minusDays(10), null, false);
        entities.flush();
        entities.clear();

        assertThat(assignments.findAccessibleRegions(manager.getId(), date))
                .extracting(Region::getId)
                .containsExactlyInAnyOrder(beginsToday.getId(), endsToday.getId());
        assertThat(assignments.hasActiveAssignment(manager.getId(), beginsToday.getId(), date)).isTrue();
        assertThat(assignments.hasActiveAssignment(manager.getId(), endsToday.getId(), date)).isTrue();
        for (Region region : new Region[]{expired, future, inactive}) {
            assertThat(assignments.hasActiveAssignment(manager.getId(), region.getId(), date)).isFalse();
        }
        assertThat(assignments.findAccessibleRegions(manager.getId() + 1, date)).isEmpty();
    }

    private Region assignment(AppUser manager, String code, LocalDate start, LocalDate end, boolean active) {
        Region region = new Region();
        region.setCode(code);
        region.setName(code);
        region.setActive(active);
        entities.persist(region);
        RegionAssignment assignment = new RegionAssignment();
        assignment.setRegion(region);
        assignment.setManager(manager);
        assignment.setStartDate(start);
        assignment.setEndDate(end);
        entities.persist(assignment);
        return region;
    }
}
