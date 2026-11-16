package by.regsales.catalog;

import by.regsales.domain.AppUser;
import by.regsales.domain.Region;
import by.regsales.domain.Role;
import by.regsales.domain.RoleCode;
import by.regsales.repository.RegionAssignmentRepository;
import by.regsales.repository.RegionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegionScopeServiceTest {
    private final RegionRepository regions = mock(RegionRepository.class);
    private final RegionAssignmentRepository assignments = mock(RegionAssignmentRepository.class);
    private final RegionScopeService service = new RegionScopeService(regions, assignments);
    private AppUser manager;

    @BeforeEach
    void setup() {
        manager = new AppUser();
        manager.setId(17L);
        manager.setActive(true);
        Role role = new Role();
        role.setCode(RoleCode.MANAGER);
        manager.setRole(role);
    }

    @Test
    void newlyRegisteredManagerHasNoCommercialRegions() {
        when(assignments.findAccessibleRegions(eq(17L), any(LocalDate.class))).thenReturn(List.of());
        assertThat(service.accessibleRegions(manager)).isEmpty();
        verifyNoInteractions(regions);
        assertThatThrownBy(() -> service.requireRegionAccess(manager, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managerCanAccessOnlyAssignedRegion() {
        Region region = new Region();
        region.setId(3L);
        when(assignments.findAccessibleRegions(eq(17L), any(LocalDate.class))).thenReturn(List.of(region));
        when(assignments.hasActiveAssignment(eq(17L), eq(3L), any(LocalDate.class))).thenReturn(true);
        assertThat(service.accessibleRegions(manager)).containsExactly(region);
        assertThatCode(() -> service.requireRegionAccess(manager, 3L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireRegionAccess(manager, 4L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void analystCanSeeAllActiveRegions() {
        manager.getRole().setCode(RoleCode.ANALYST);
        Region region = new Region();
        when(regions.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(region));
        assertThat(service.accessibleRegions(manager)).containsExactly(region);
        verifyNoInteractions(assignments);
    }

    @Test
    void blockedUserCannotUseScopeService() {
        manager.setActive(false);
        assertThatThrownBy(() -> service.accessibleRegions(manager)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requireRegionAccess(manager, 1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(assignments, regions);
    }
}
