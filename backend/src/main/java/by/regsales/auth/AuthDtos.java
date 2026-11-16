package by.regsales.auth;
import by.regsales.domain.*;
import jakarta.validation.constraints.*;
public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(
        @NotBlank @Pattern(regexp="[a-zA-Z0-9._-]{3,60}",message="Логин: 3–60 латинских букв, цифр, точек, дефисов или подчёркиваний") String login,
        @NotBlank @Email(message="Введите корректный email") @Size(max=254) String email,
        @NotBlank @Size(min=2,max=150,message="Имя: от 2 до 150 символов") String fullName,
        @NotBlank @Size(min=8,max=72,message="Пароль: от 8 до 72 символов") String password) {}
    public record LoginRequest(@NotBlank @Size(max=60) String login, @NotBlank @Size(max=72) String password) {}
    public record UserView(Long id,String login,String email,String fullName,RoleCode role,String roleName,boolean active) {
        public static UserView from(AppUser u) { return new UserView(u.getId(),u.getLogin(),u.getEmail(),u.getFullName(),u.getRole().getCode(),u.getRole().getName(),u.isActive()); }
    }
    public record UserPatch(RoleCode role,Boolean active) {}
    public record RoleView(RoleCode code,String name) {}
}
