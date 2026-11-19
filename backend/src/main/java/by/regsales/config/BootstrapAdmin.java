package by.regsales.config;

import by.regsales.auth.AuthDtos.RegisterRequest;
import by.regsales.domain.AppUser;
import by.regsales.domain.RoleCode;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.RoleRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final Validator validator;

    @Value("${app.bootstrap-admin.login:}") private String login;
    @Value("${app.bootstrap-admin.email:}") private String email;
    @Value("${app.bootstrap-admin.password:}") private String password;
    @Value("${app.bootstrap-admin.full-name}") private String fullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (login.isBlank() && email.isBlank() && password.isBlank()) return;
        var role = roles.lockByCode(RoleCode.ADMIN).orElseThrow();
        if (users.existsByRoleCode(RoleCode.ADMIN)) return;

        var request = new RegisterRequest(login, email, fullName.strip(), password);
        if (!validator.validate(request).isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Проверьте параметры BOOTSTRAP_ADMIN_LOGIN/EMAIL/PASSWORD/FULL_NAME");
        }
        var normalizedLogin = login.toLowerCase(Locale.ROOT);
        var normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (users.existsByLogin(normalizedLogin) || users.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("Логин или email для первого администратора уже используется");
        }

        var user = new AppUser();
        user.setLogin(normalizedLogin);
        user.setEmail(normalizedEmail);
        user.setFullName(request.fullName());
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        users.saveAndFlush(user);
    }
}
