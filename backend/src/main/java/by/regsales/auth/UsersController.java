package by.regsales.auth;
import by.regsales.common.ApiException;
import by.regsales.domain.*;
import by.regsales.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.*;
import static by.regsales.auth.AuthDtos.*;
@RestController @RequestMapping("/api") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class UsersController {
    private final AppUserRepository users;
    private final RoleRepository roles;
    @GetMapping("/users") @Transactional(readOnly=true) public List<UserView> list() { return users.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(UserView::from).toList(); }
    @GetMapping("/roles") public List<RoleView> roles() { return roles.findAll().stream().map(r->new RoleView(r.getCode(),r.getName())).toList(); }
    @PatchMapping("/users/{id}") @Transactional public UserView patch(@PathVariable Long id,@Valid @RequestBody UserPatch patch,Authentication authentication) {
        // Serialize changes to administrator membership across concurrent requests.
        roles.lockByCode(RoleCode.ADMIN).orElseThrow();
        var actor=users.findByLogin(authentication.getName()).orElseThrow();
        if (!actor.isActive() || actor.getRole().getCode()!=RoleCode.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Недостаточно прав для выполнения операции");
        var user=users.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","Пользователь не найден"));
        if (patch.active()==null&&patch.role()==null) throw new ApiException(HttpStatus.BAD_REQUEST,"EMPTY_UPDATE","Укажите роль или состояние учётной записи");
        if (user.getLogin().equals(authentication.getName()) && (Boolean.FALSE.equals(patch.active()) || patch.role()!=null && patch.role()!=RoleCode.ADMIN)) throw new ApiException(HttpStatus.CONFLICT,"SELF_LOCKOUT","Нельзя отключить или понизить собственную учётную запись");
        if (user.isActive() && user.getRole().getCode()==RoleCode.ADMIN &&
            (Boolean.FALSE.equals(patch.active()) || patch.role()!=null && patch.role()!=RoleCode.ADMIN) && users.countByRoleCodeAndActiveTrue(RoleCode.ADMIN)<=1) {
            throw new ApiException(HttpStatus.CONFLICT,"LAST_ADMIN","Нельзя отключить последнего активного администратора");
        }
        if (patch.role()!=null) user.setRole(roles.findByCode(patch.role()).orElseThrow());
        if (patch.active()!=null) user.setActive(patch.active());
        return UserView.from(users.saveAndFlush(user));
    }
}
