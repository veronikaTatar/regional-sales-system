package by.regsales.catalog;

import by.regsales.domain.AppUser;
import by.regsales.domain.Region;
import by.regsales.domain.RoleCode;
import by.regsales.repository.RegionAssignmentRepository;
import by.regsales.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Reusable scope check for commercial modules; appointments use Belarus calendar dates. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionScopeService {
    private final RegionRepository regions;
    private final RegionAssignmentRepository assignments;

    public List<Region> accessibleRegions(AppUser user) {
        requireActive(user);
        if (user.getRole().getCode() == RoleCode.MANAGER) {
            return assignments.findAccessibleRegions(user.getId(), today());
        }
        return regions.findAllByActiveTrueOrderByNameAsc();
    }

    public void requireRegionAccess(AppUser user, Long regionId) {
        requireActive(user);
        if (user.getRole().getCode() == RoleCode.MANAGER
                && !assignments.hasActiveAssignment(user.getId(), regionId, today())) {
            throw new AccessDeniedException("Нет доступа к данным выбранного региона");
        }
    }

    private void requireActive(AppUser user) {
        if (!user.isActive()) throw new AccessDeniedException("Учётная запись заблокирована");
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Minsk"));
    }
}
