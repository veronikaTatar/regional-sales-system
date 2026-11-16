package by.regsales.auth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"app.google.client-id=test-client","app.google.client-secret=test-secret","app.google.redirect-uri=http://localhost:8080/login/oauth2/code/google"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class GoogleFlowConfigurationTest {
    @Autowired MockMvc mvc;
    @Test void startsOidcFlowWithExplicitBackendCallbackAndState() throws Exception {
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk()).andExpect(jsonPath("$.google").value(true));
        var result=mvc.perform(get("/oauth2/authorization/google").header("Host","localhost:5173")).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
            .contains("state=").contains("scope=openid%20profile%20email")
            .contains("redirect_uri=http://localhost:8080/login/oauth2/code/google");
    }
    @Test void rejectsCallbackWithoutMatchingStateBeforeAnyTokenRequest() throws Exception {
        mvc.perform(get("/login/oauth2/code/google").param("code","untrusted-code").param("state","untrusted-state"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost:5173/login?error=google"));
    }
}
