package by.regsales.auth;

import by.regsales.common.ApiException;
import by.regsales.domain.AppUser;
import by.regsales.domain.RegionAssignment;
import by.regsales.domain.RoleCode;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.RegionAssignmentRepository;
import by.regsales.repository.RegionRepository;
import by.regsales.repository.RoleRepository;
import by.regsales.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static by.regsales.auth.AuthDtos.UserView;
import static by.regsales.auth.UserAdministrationDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdministrationService {
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RegionRepository regions;
    private final RegionAssignmentRepository assignments;
    private final AuthService accounts;

    public List<UserListEntry> listUsers() {
        var assignedUsers = assignments.findAssignedManagerIds();
        return users.findAll(Sort.by("id")).stream()
                .map(user -> new UserListEntry(UserView.from(user), assignedUsers.contains(user.getId())))
                .toList();
    }

    public List<RoleView> listRoles() {
        return roles.findAll(Sort.by("id")).stream().map(role -> new RoleView(role.getCode(), role.getName())).toList();
    }

    @Transactional
    public UserView createUser(UserCreate request, AppPrincipal actor) {
        lockAndCheckAdministrator(actor);
        return UserView.from(accounts.createLocalAccount(request.login(), request.email(), request.fullName(),
                request.password(), request.role()));
    }

    @Transactional
    public UserView updateUser(Long id, UserPatch patch, AppPrincipal actor) {
        lockAndCheckAdministrator(actor);
        var user = findUser(id);
        if (patch.login() == null && patch.email() == null && patch.fullName() == null
                && patch.password() == null && patch.role() == null && patch.active() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Укажите данные, которые нужно изменить");
        }

        boolean blocking = Boolean.FALSE.equals(patch.active());
        boolean demoting = patch.role() != null && patch.role() != RoleCode.ADMIN;
        if (id.equals(actor.getId()) && (blocking || demoting)) {
            throw new ApiException(HttpStatus.CONFLICT, "SELF_LOCKOUT",
                    "Нельзя заблокировать себя или снять собственную роль администратора");
        }
        if (user.isActive() && user.getRole().getCode() == RoleCode.ADMIN && (blocking || demoting)
                && users.countByRoleCodeAndActiveTrue(RoleCode.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "В системе должен остаться активный администратор");
        }

        accounts.updateProfile(user, patch.login(), patch.email(), patch.fullName());
        if (patch.password() != null) user.setPasswordHash(accounts.hashPassword(patch.password()));
        if (patch.password() != null || (user.isActive() && blocking)) {
            user.setSessionVersion(user.getSessionVersion() + 1);
        }
        if (patch.role() != null) user.setRole(roles.findByCode(patch.role()).orElseThrow());
        if (patch.active() != null) user.setActive(patch.active());
        return UserView.from(users.saveAndFlush(user));
    }

    public List<RegionAssignmentView> listAssignments(Long userId) {
        findUser(userId);
        return assignments.findAllByManagerIdOrderByStartDateDescIdDesc(userId).stream()
                .map(RegionAssignmentView::from).toList();
    }

    @Transactional
    public RegionAssignmentView saveAssignment(Long userId, Long assignmentId, RegionAssignmentRequest request,
                                               AppPrincipal actor) {
        lockAndCheckAdministrator(actor);
        var user = findUser(userId);
        if (user.getRole().getCode() != RoleCode.MANAGER) {
            throw new ApiException(HttpStatus.CONFLICT, "MANAGER_REQUIRED", "Регионы можно закрепить только за менеджером");
        }

        var assignment = assignmentId == null ? new RegionAssignment() : findAssignment(userId, assignmentId);
        var region = regions.findById(request.regionId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "REGION_NOT_FOUND", "Регион не найден"));
        if (!region.isActive() && (assignment.getRegion() == null || !region.getId().equals(assignment.getRegion().getId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "REGION_INACTIVE", "Выберите действующий регион");
        }
        checkDateRange(request.startDate(), "startDate");
        if (request.endDate() != null) {
            checkDateRange(request.endDate(), "endDate");
            if (request.endDate().isBefore(request.startDate())) {
                throw invalidDate("endDate", "Дата окончания должна быть не раньше даты начала");
            }
        }
        if (assignments.hasOverlappingPeriod(userId, region.getId(), request.startDate(), request.endDate(), assignmentId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSIGNMENT_OVERLAP",
                    "Для этого региона уже есть назначение на пересекающийся период");
        }
        assignment.setManager(user);
        assignment.setRegion(region);
        assignment.setStartDate(request.startDate());
        assignment.setEndDate(request.endDate());
        return RegionAssignmentView.from(assignments.saveAndFlush(assignment));
    }

    @Transactional
    public void deleteAssignment(Long userId, Long assignmentId, AppPrincipal actor) {
        lockAndCheckAdministrator(actor);
        assignments.delete(findAssignment(userId, assignmentId));
    }

    private AppUser findUser(Long id) {
        return users.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Пользователь не найден"));
    }

    private RegionAssignment findAssignment(Long userId, Long assignmentId) {
        return assignments.findByIdAndManagerId(assignmentId, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "Назначение не найдено"));
    }

    private void lockAndCheckAdministrator(AppPrincipal actor) {
        // Serialize account and assignment writes before checking the actor or overlapping dates.
        roles.lockByCode(RoleCode.ADMIN).orElseThrow();
        var account = users.findById(actor.getId()).orElse(null);
        if (account == null || !account.isActive() || account.getSessionVersion() != actor.getSessionVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Войдите в систему");
        }
        if (account.getRole().getCode() != RoleCode.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Недостаточно прав для выполнения операции");
        }
    }

    private void checkDateRange(LocalDate date, String field) {
        if (date.getYear() < 1000 || date.getYear() > 9999) {
            throw invalidDate(field, "Введите дату с годом от 1000 до 9999");
        }
    }

    private ApiException invalidDate(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNMENT_PERIOD",
                "Проверьте даты назначения", Map.of(field, message));
    }
}
