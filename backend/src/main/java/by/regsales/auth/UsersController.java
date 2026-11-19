package by.regsales.auth;

import by.regsales.security.AppPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static by.regsales.auth.AuthDtos.UserView;
import static by.regsales.auth.UserAdministrationDtos.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsersController {
    private final UserAdministrationService administration;

    @GetMapping("/users")
    public List<UserListEntry> listUsers() {
        return administration.listUsers();
    }

    @GetMapping("/roles")
    public List<RoleView> listRoles() {
        return administration.listRoles();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody UserCreate request, @AuthenticationPrincipal AppPrincipal actor) {
        return administration.createUser(request, actor);
    }

    @PatchMapping("/users/{id}")
    public UserView updateUser(@PathVariable Long id, @Valid @RequestBody UserPatch request,
                               @AuthenticationPrincipal AppPrincipal actor) {
        return administration.updateUser(id, request, actor);
    }

    @GetMapping("/users/{id}/region-assignments")
    public List<RegionAssignmentView> listAssignments(@PathVariable Long id) {
        return administration.listAssignments(id);
    }

    @PostMapping("/users/{id}/region-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public RegionAssignmentView createAssignment(@PathVariable Long id, @Valid @RequestBody RegionAssignmentRequest request,
                                                  @AuthenticationPrincipal AppPrincipal actor) {
        return administration.saveAssignment(id, null, request, actor);
    }

    @PutMapping("/users/{id}/region-assignments/{assignmentId}")
    public RegionAssignmentView updateAssignment(@PathVariable Long id, @PathVariable Long assignmentId,
                                                  @Valid @RequestBody RegionAssignmentRequest request,
                                                  @AuthenticationPrincipal AppPrincipal actor) {
        return administration.saveAssignment(id, assignmentId, request, actor);
    }

    @DeleteMapping("/users/{id}/region-assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable Long id, @PathVariable Long assignmentId,
                                 @AuthenticationPrincipal AppPrincipal actor) {
        administration.deleteAssignment(id, assignmentId, actor);
    }
}
