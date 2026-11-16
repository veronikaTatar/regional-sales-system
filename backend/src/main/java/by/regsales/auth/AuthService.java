package by.regsales.auth;
import by.regsales.common.ApiException;
import by.regsales.domain.*;
import by.regsales.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
@Service @RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    @Transactional public AppUser register(AuthDtos.RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length>72) throw new ApiException(HttpStatus.BAD_REQUEST,"PASSWORD_TOO_LONG","Пароль должен занимать не более 72 байт UTF-8");
        var login=request.login().toLowerCase(Locale.ROOT); var email=request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByLogin(login)||users.existsByEmail(email)) throw new ApiException(HttpStatus.CONFLICT,"ACCOUNT_EXISTS","Логин или email уже используется");
        var user=new AppUser(); user.setLogin(login); user.setEmail(email); user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(roles.findByCode(RoleCode.MANAGER).orElseThrow());
        return users.saveAndFlush(user);
    }
    @Transactional(readOnly=true) public AppUser current(String login) {
        return users.findByLogin(login).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Войдите в систему"));
    }
}
