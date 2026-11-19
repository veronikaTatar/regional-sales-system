package by.regsales.auth;

import by.regsales.common.ApiException;
import by.regsales.domain.AppUser;
import by.regsales.domain.RoleCode;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;

    @Transactional
    public AppUser register(AuthDtos.RegisterRequest request) {
        return createLocalAccount(request.login(), request.email(), request.fullName(), request.password(), RoleCode.MANAGER);
    }

    AppUser createLocalAccount(String login, String email, String fullName, String password, RoleCode role) {
        var user = new AppUser();
        updateProfile(user, login, email, fullName);
        user.setPasswordHash(hashPassword(password));
        user.setRole(roles.findByCode(role).orElseThrow());
        return users.saveAndFlush(user);
    }

    void updateProfile(AppUser user, String login, String email, String fullName) {
        var normalizedLogin = login == null ? null : login.toLowerCase(Locale.ROOT);
        var normalizedEmail = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        var name = fullName == null ? null : fullName.strip();
        if (name != null && name.length() < 2) {
            throw fieldError("VALIDATION_ERROR", "fullName", AuthDtos.NAME_MESSAGE);
        }
        if (normalizedEmail != null && normalizedEmail.length() > 254) {
            throw fieldError("VALIDATION_ERROR", "email", "Email должен содержать не более 254 символов");
        }

        var conflicts = new LinkedHashMap<String, String>();
        if (normalizedLogin != null && users.findByLogin(normalizedLogin)
                .filter(other -> !Objects.equals(other.getId(), user.getId())).isPresent()) {
            conflicts.put("login", "Этот логин уже используется");
        }
        if (normalizedEmail != null && users.findByEmail(normalizedEmail)
                .filter(other -> !Objects.equals(other.getId(), user.getId())).isPresent()) {
            conflicts.put("email", "Этот email уже используется");
        }
        if (!conflicts.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "Логин или email уже используется", conflicts);
        }

        if (normalizedLogin != null) user.setLogin(normalizedLogin);
        if (normalizedEmail != null) user.setEmail(normalizedEmail);
        if (name != null) user.setFullName(name);
    }

    String hashPassword(String password) {
        if (password.isBlank()) {
            throw fieldError("VALIDATION_ERROR", "password", "Введите пароль");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw fieldError("PASSWORD_TOO_LONG", "password", "Пароль должен занимать не более 72 байт UTF-8");
        }
        return passwords.encode(password);
    }

    @Transactional(readOnly = true)
    public AppUser current(Long id) {
        return users.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Войдите в систему"));
    }

    private ApiException fieldError(String code, String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, "Проверьте заполнение формы", Map.of(field, message));
    }
}
