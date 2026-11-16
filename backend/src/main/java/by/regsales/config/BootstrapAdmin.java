package by.regsales.config;
import by.regsales.domain.*;
import by.regsales.repository.*;
import jakarta.validation.Validator;
import by.regsales.auth.AuthDtos.RegisterRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final AppUserRepository users; private final RoleRepository roles; private final PasswordEncoder encoder; private final Validator validator;
    @Value("${app.bootstrap-admin.login:}") private String login;
    @Value("${app.bootstrap-admin.email:}") private String email;
    @Value("${app.bootstrap-admin.password:}") private String password;
    @Value("${app.bootstrap-admin.full-name}") private String fullName;
    public BootstrapAdmin(AppUserRepository users,RoleRepository roles,PasswordEncoder encoder,Validator validator) {this.users=users;this.roles=roles;this.encoder=encoder;this.validator=validator;}
    @Override @Transactional public void run(ApplicationArguments args) {
        if (login.isBlank()&&email.isBlank()&&password.isBlank()) return;
        if (!validator.validate(new RegisterRequest(login,email,fullName,password)).isEmpty() || password.getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalStateException("Проверьте BOOTSTRAP_ADMIN_LOGIN/EMAIL/PASSWORD/FULL_NAME: некорректные параметры администратора");
        login=login.toLowerCase(Locale.ROOT); email=email.toLowerCase(Locale.ROOT);
        var existing=users.findByLogin(login).orElse(null);
        if (existing!=null) {
            if (existing.getRole().getCode()!=RoleCode.ADMIN || !existing.getEmail().equals(email)) throw new IllegalStateException("Учётная запись BOOTSTRAP_ADMIN уже существует с другими данными. Автоматическое повышение прав запрещено");
            return;
        }
        var user=new AppUser();user.setLogin(login);user.setEmail(email);user.setFullName(fullName);user.setPasswordHash(encoder.encode(password));user.setRole(roles.findByCode(RoleCode.ADMIN).orElseThrow());users.saveAndFlush(user);
    }
}
