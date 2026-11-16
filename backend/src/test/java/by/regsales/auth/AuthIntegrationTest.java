package by.regsales.auth;
import by.regsales.domain.*;
import by.regsales.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    static int sequence;
    @BeforeEach void roles() { for(var code:RoleCode.values()) if(roles.findByCode(code).isEmpty()) {var role=new Role();role.setCode(code);role.setName(code.name());roles.save(role);} }
    String login() {return "test_"+(++sequence);}
    String registration(String login) {return "{\"login\":\""+login+"\",\"email\":\""+login+"@example.com\",\"fullName\":\"Тестовый пользователь\",\"password\":\"Password123!\"}";}
    MvcResult register(String login) throws Exception {return mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(registration(login))).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("MANAGER")).andReturn();}
    @Test void publicEndpointsAndAnonymousAccess() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk()).andExpect(jsonPath("$.google").value(false));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(registration(login()))).andExpect(status().isForbidden());
    }
    @Test void registrationHashesPasswordAndCreatesAuthenticatedSession() throws Exception {
        var login=login(); var result=register(login); var session=(MockHttpSession)result.getRequest().getSession(false);
        assertThat(session).isNotNull();var user=users.findByLogin(login).orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2");assertThat(encoder.matches("Password123!",user.getPasswordHash())).isTrue();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.login").value(login)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(registration(login))).andExpect(status().isConflict());
    }
    @Test void loginRejectsWrongPasswordAndLogoutInvalidatesSession() throws Exception {
        var login=login();register(login);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"login\":\""+login+"\",\"password\":\"wrongpass\"}")).andExpect(status().isUnauthorized());
        var result=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"login\":\""+login+"\",\"password\":\"Password123!\"}")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void validatesInputAndDoesNotAcceptPublicRoleAssignment() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"login\":\"x\",\"email\":\"bad\",\"fullName\":\"x\",\"password\":\"short\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.login").exists());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(registration(login()).replace("\"password\":","\"role\":\"ADMIN\",\"password\":"))).andExpect(status().isBadRequest());
        var request=new AuthDtos.RegisterRequest(login(),login()+"@example.com","Проверка","я".repeat(40));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
    }
    @Test void permissionsAndBlockingAreRecheckedForExistingSession() throws Exception {
        var login=login();var result=register(login);var session=(MockHttpSession)result.getRequest().getSession(false);
        var user=users.findByLogin(login).orElseThrow(); user.setRole(roles.findByCode(RoleCode.ADMIN).orElseThrow());users.saveAndFlush(user);
        mvc.perform(get("/api/users").session(session)).andExpect(status().isOk());
        mvc.perform(patch("/api/users/"+user.getId()).session(session).with(csrf()).contentType("application/json").content("{\"active\":false}")).andExpect(status().isConflict());
        user.setRole(roles.findByCode(RoleCode.MANAGER).orElseThrow());users.saveAndFlush(user);
        mvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
        user.setActive(false);users.saveAndFlush(user);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());assertThat(session.isInvalid()).isTrue();
    }
    @Test void administratorCanAssignRolesAndBlockAnotherAccount() throws Exception {
        var adminLogin=login();var adminResult=register(adminLogin);var adminSession=(MockHttpSession)adminResult.getRequest().getSession(false);
        var admin=users.findByLogin(adminLogin).orElseThrow();admin.setRole(roles.findByCode(RoleCode.ADMIN).orElseThrow());users.saveAndFlush(admin);
        var targetLogin=login();var targetResult=register(targetLogin);var targetSession=(MockHttpSession)targetResult.getRequest().getSession(false);var target=users.findByLogin(targetLogin).orElseThrow();
        mvc.perform(get("/api/roles").session(adminSession)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(patch("/api/users/"+target.getId()).session(adminSession).with(csrf()).contentType("application/json").content("{\"role\":\"ANALYST\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ANALYST"));
        mvc.perform(get("/api/auth/me").session(targetSession)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ANALYST"));
        mvc.perform(patch("/api/users/"+target.getId()).session(adminSession).with(csrf()).contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/auth/me").session(targetSession)).andExpect(status().isUnauthorized());
    }
    @Test void actualCsrfBootstrapTokenIsAcceptedAndRotatedAfterLogin() throws Exception {
        var bootstrap=mvc.perform(get("/api/auth/csrf")).andReturn();var session=(MockHttpSession)bootstrap.getRequest().getSession(false);
        var token=json.readTree(bootstrap.getResponse().getContentAsString()).get("token").asText();
        var oldSessionId=session.getId();
        mvc.perform(post("/api/auth/register").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content(registration(login()))).andExpect(status().isCreated());
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",token)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var next=mvc.perform(get("/api/auth/csrf").session(session)).andReturn();var nextToken=json.readTree(next.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",nextToken)).andExpect(status().isNoContent());
    }
}
