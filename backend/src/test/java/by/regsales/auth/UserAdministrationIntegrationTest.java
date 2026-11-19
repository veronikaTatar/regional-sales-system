package by.regsales.auth;

import by.regsales.domain.AppUser;
import by.regsales.domain.Region;
import by.regsales.domain.Role;
import by.regsales.domain.RoleCode;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.RegionRepository;
import by.regsales.repository.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAdministrationIntegrationTest {
    private static final String PASSWORD = "InitialPassword123!";
    private static final String NEW_PASSWORD = "ReplacementPassword123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RegionRepository regions;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void ensureRolesExist() {
        for (var code : RoleCode.values()) {
            if (roles.findByCode(code).isEmpty()) {
                var role = new Role();
                role.setCode(code);
                role.setName(code.name());
                roles.save(role);
            }
        }
    }

    @Test
    void creationAndRegionalWritesRequireAnAdministratorAndCsrf() throws Exception {
        var manager = signInAs(RoleCode.MANAGER);
        var admin = signInAs(RoleCode.ADMIN);
        var assignmentPath = "/api/users/" + manager.id() + "/region-assignments";

        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get(assignmentPath).session(manager.session())).andExpect(status().isForbidden());
        write(post("/api/users").session(manager.session()), newAccount(uniqueLogin(), RoleCode.ADMIN))
                .andExpect(status().isForbidden());
        write(patch("/api/users/" + manager.id()).session(manager.session()), Map.of("role", "ADMIN"))
                .andExpect(status().isForbidden());
        write(post(assignmentPath).session(manager.session()), Map.of())
                .andExpect(status().isForbidden());
        mvc.perform(delete(assignmentPath + "/1").session(manager.session()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users").session(admin.session()).contentType("application/json")
                        .content(json.writeValueAsString(newAccount(uniqueLogin(), RoleCode.ANALYST))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void administratorCreatesAndEditsAnAccountWithoutChangingTheirOwnSession() throws Exception {
        var admin = signInAs(RoleCode.ADMIN);
        var login = uniqueLogin();
        var created = write(post("/api/users").session(admin.session()), newAccount(login.toUpperCase(Locale.ROOT), RoleCode.MANAGER))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.login").value(login))
                .andExpect(jsonPath("$.email").value(login + "@example.com"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        var path = "/api/users/" + id;
        assertThat(encoder.matches(PASSWORD, users.findById(id).orElseThrow().getPasswordHash())).isTrue();
        mvc.perform(get("/api/auth/me").session(admin.session())).andExpect(jsonPath("$.id").value(admin.id()));
        var employeeSession = login(login, PASSWORD);

        write(post("/api/users").session(admin.session()), newAccount(login, RoleCode.MANAGER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.login").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        write(patch(path).session(admin.session()), Map.of("login", admin.login()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.login").exists());

        var newLogin = uniqueLogin();
        write(patch(path).session(admin.session()), Map.of(
                "login", newLogin, "fullName", "  Елена Петрова  ", "email", "UPDATED_" + login + "@example.com",
                "role", "ANALYST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Елена Петрова"))
                .andExpect(jsonPath("$.role").value("ANALYST"));
        mvc.perform(get("/api/auth/me").session(employeeSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.login").value(newLogin))
                .andExpect(jsonPath("$.email").value("updated_" + login + "@example.com"))
                .andExpect(jsonPath("$.role").value("ANALYST"));
        write(post("/api/auth/login"), Map.of("login", login, "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login(newLogin, PASSWORD);
    }

    @Test
    void blockingAndPasswordChangesRevokeOldSessionsAndRejectInvalidCombinedUpdates() throws Exception {
        var admin = signInAs(RoleCode.ADMIN);
        var employee = signInAs(RoleCode.MANAGER);
        var path = "/api/users/" + employee.id();

        write(patch(path).session(admin.session()), Map.of("password", "я".repeat(40), "active", false, "fullName", "Не сохранится"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
        assertThat(users.findById(employee.id()).orElseThrow().isActive()).isTrue();
        assertThat(users.findById(employee.id()).orElseThrow().getFullName()).isEqualTo("Тестовый сотрудник");

        write(patch(path).session(admin.session()), Map.of("active", false)).andExpect(status().isOk());
        write(post("/api/auth/login"), Map.of("login", employee.login(), "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        write(patch(path).session(admin.session()), Map.of("active", true)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(employee.session())).andExpect(status().isUnauthorized());
        var afterReactivation = login(employee.login(), PASSWORD);

        write(patch(path).session(admin.session()), Map.of("password", NEW_PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/auth/me").session(afterReactivation)).andExpect(status().isUnauthorized());
        write(post("/api/auth/login"), Map.of("login", employee.login(), "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login(employee.login(), NEW_PASSWORD);

        write(patch("/api/users/" + admin.id()).session(admin.session()), Map.of("role", "MANAGER"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
    }

    @Test
    void datedAssignmentsCanBeChangedAndRevokedWithoutAccessToOtherRegions() throws Exception {
        var admin = signInAs(RoleCode.ADMIN);
        var employee = signInAs(RoleCode.MANAGER);
        var anotherEmployee = signInAs(RoleCode.MANAGER);
        var region = createRegion(true);
        var otherRegion = createRegion(true);
        var inactiveRegion = createRegion(false);
        var today = LocalDate.now(ZoneId.of("Europe/Minsk"));
        var path = "/api/users/" + employee.id() + "/region-assignments";

        mvc.perform(get("/api/regions").session(employee.session())).andExpect(jsonPath("$.length()").value(0));
        write(post(path).session(admin.session()), Map.of("regionId", region.getId(),
                        "startDate", today.toString(), "endDate", today.minusDays(1).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.endDate").exists());
        write(post(path).session(admin.session()), Map.of("regionId", inactiveRegion.getId(), "startDate", today.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGION_INACTIVE"));
        var created = write(post(path).session(admin.session()), Map.of("regionId", region.getId(),
                        "startDate", today.minusDays(1).toString(), "endDate", today.toString()))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/regions").session(employee.session()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(region.getId()));

        write(post(path).session(admin.session()), Map.of("regionId", region.getId(), "startDate", today.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_OVERLAP"));
        write(put(path + "/" + id).session(admin.session()), Map.of("regionId", otherRegion.getId(), "startDate", today.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.regionId").value(otherRegion.getId()));
        mvc.perform(get("/api/regions").session(employee.session()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(otherRegion.getId()));

        var anotherPath = "/api/users/" + anotherEmployee.id() + "/region-assignments/" + id;
        mvc.perform(delete(anotherPath).session(admin.session()).with(csrf())).andExpect(status().isNotFound());

        write(patch("/api/users/" + employee.id()).session(admin.session()), Map.of("role", "ANALYST"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users").session(admin.session()))
                .andExpect(jsonPath("$[?(@.login == '" + employee.login() + "')].hasRegionAssignments", hasItem(true)));
        mvc.perform(get(path).session(admin.session())).andExpect(jsonPath("$[0].id").value(id));
        write(post(path).session(admin.session()), Map.of("regionId", region.getId(), "startDate", today.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MANAGER_REQUIRED"));
        mvc.perform(delete(path + "/" + id).session(admin.session()).with(csrf())).andExpect(status().isNoContent());
        write(patch("/api/users/" + employee.id()).session(admin.session()), Map.of("role", "MANAGER"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/regions").session(employee.session())).andExpect(jsonPath("$.length()").value(0));
    }

    private record UserSession(long id, String login, MockHttpSession session) {}

    private UserSession signInAs(RoleCode role) throws Exception {
        var login = uniqueLogin();
        var user = new AppUser();
        user.setLogin(login);
        user.setEmail(login + "@example.com");
        user.setFullName("Тестовый сотрудник");
        user.setRole(roles.findByCode(role).orElseThrow());
        user.setPasswordHash(encoder.encode(PASSWORD));
        users.saveAndFlush(user);
        return new UserSession(user.getId(), login, login(login, PASSWORD));
    }

    private MockHttpSession login(String login, String password) throws Exception {
        var result = write(post("/api/auth/login"), Map.of("login", login, "password", password))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions write(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(csrf()).contentType("application/json").content(json.writeValueAsString(body)));
    }

    private Map<String, String> newAccount(String login, RoleCode role) {
        return Map.of("login", login, "email", login + "@example.com", "fullName", "Новый сотрудник",
                "role", role.name(), "password", PASSWORD);
    }

    private String uniqueLogin() {
        return "staff_" + UUID.randomUUID();
    }

    private Region createRegion(boolean active) {
        var region = new Region();
        region.setCode(UUID.randomUUID().toString().substring(0, 12));
        region.setName("Регион " + region.getCode());
        region.setActive(active);
        return regions.saveAndFlush(region);
    }
}
