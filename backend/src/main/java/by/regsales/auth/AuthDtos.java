package by.regsales.auth;
import by.regsales.domain.*;
import jakarta.validation.constraints.*;
public final class AuthDtos {
    private AuthDtos() {}
    static final String LOGIN_PATTERN = "[a-zA-Z0-9._-]{3,60}";
    static final String LOGIN_MESSAGE = "Логин: 3–60 латинских букв, цифр, точек, дефисов или подчёркиваний";
    static final String EMAIL_MESSAGE = "Введите корректный email";
    static final String NAME_MESSAGE = "ФИО должно содержать от 2 до 150 символов";
    static final String PASSWORD_MESSAGE = "Пароль должен содержать от 8 до 72 символов";

    public record RegisterRequest(
        @NotBlank @Pattern(regexp = LOGIN_PATTERN, message = LOGIN_MESSAGE) String login,
        @NotBlank @Email(message = EMAIL_MESSAGE) @Size(max = 254) String email,
        @NotBlank @Size(min = 2, max = 150, message = NAME_MESSAGE) String fullName,
        @NotBlank @Size(min = 8, max = 72, message = PASSWORD_MESSAGE) String password) {}
    public record LoginRequest(@NotBlank @Size(max=60) String login, @NotBlank @Size(max=72) String password) {}
    public record UserView(Long id,String login,String email,String fullName,RoleCode role,String roleName,boolean active) {
        public static UserView from(AppUser u) { return new UserView(u.getId(),u.getLogin(),u.getEmail(),u.getFullName(),u.getRole().getCode(),u.getRole().getName(),u.isActive()); }
    }
}
