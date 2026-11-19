package by.regsales.auth;

import by.regsales.domain.RegionAssignment;
import by.regsales.domain.RoleCode;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

import static by.regsales.auth.AuthDtos.*;

public final class UserAdministrationDtos {
    private UserAdministrationDtos() {}

    public record UserCreate(
            @NotBlank @Pattern(regexp = LOGIN_PATTERN, message = LOGIN_MESSAGE) String login,
            @NotBlank @Email(message = EMAIL_MESSAGE) @Size(max = 254) String email,
            @NotBlank @Size(min = 2, max = 150, message = NAME_MESSAGE) String fullName,
            @NotBlank @Size(min = 8, max = 72, message = PASSWORD_MESSAGE) String password,
            @NotNull(message = "Выберите роль") RoleCode role) {}

    public record UserPatch(
            @Pattern(regexp = LOGIN_PATTERN, message = LOGIN_MESSAGE) String login,
            @Email(message = EMAIL_MESSAGE) @Size(min = 1, max = 254, message = EMAIL_MESSAGE) String email,
            @Size(min = 2, max = 150, message = NAME_MESSAGE) String fullName,
            @Size(min = 8, max = 72, message = PASSWORD_MESSAGE) String password,
            RoleCode role,
            Boolean active) {}

    public record RoleView(RoleCode code, String name) {}

    public record UserListEntry(@JsonUnwrapped UserView user, boolean hasRegionAssignments) {}

    public record RegionAssignmentRequest(
            @NotNull(message = "Выберите регион") @Positive Long regionId,
            @NotNull(message = "Укажите дату начала") LocalDate startDate,
            LocalDate endDate) {}

    public record RegionAssignmentView(Long id, Long regionId, String regionName, boolean regionActive,
                                       LocalDate startDate, LocalDate endDate) {
        static RegionAssignmentView from(RegionAssignment assignment) {
            var region = assignment.getRegion();
            return new RegionAssignmentView(assignment.getId(), region.getId(), region.getName(), region.isActive(),
                    assignment.getStartDate(), assignment.getEndDate());
        }
    }
}
