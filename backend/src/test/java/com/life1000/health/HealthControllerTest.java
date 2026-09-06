package com.life1000.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.life1000.TestCredentials;
import com.life1000.auth.*;
import com.life1000.category.CategoryController;
import com.life1000.category.CategoryService;
import com.life1000.common.ApiExceptionHandler;
import com.life1000.goal.LifeGoalController;
import com.life1000.goal.LifeGoalService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest({HealthController.class, AuthController.class, CategoryController.class, LifeGoalController.class})
@Import({SecurityConfiguration.class, AuthService.class, ApiExceptionHandler.class})
@ActiveProfiles("mysql")
class HealthControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @MockitoBean CategoryService categories;
    @MockitoBean LifeGoalService goals;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) { TestCredentials.configure(registry); }

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", TestCredentials.USERNAME, "password", TestCredentials.PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    @Test
    void loginTokenAuthenticatesHealthWithoutCreatingSession() throws Exception {
        var result = mvc.perform(get("/api/health").header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/health", "/api/health/db", "/api/categories",
            "/api/goals/001", "/api/goals/range", "/api/goals/search", "/api/auth/unknown"})
    void everyApiExceptLoginRequiresToken(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void mutationsCannotBypassAuthentication() throws Exception {
        mvc.perform(post("/api/goals/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"test\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/categories/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"test\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/goals/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(categories, goals);
    }

    @Test
    void invalidCredentialsReturnGenericError() throws Exception {
        for (var credentials : List.of(
                Map.of("username", TestCredentials.USERNAME, "password", "wrong"),
                Map.of("username", "wrong", "password", TestCredentials.PASSWORD))) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(credentials)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("账号或密码错误"));
        }
    }

    @Test
    void malformedAndTamperedTokensAreRejected() throws Exception {
        String token = login();
        int signatureStart = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signatureStart)
                + (token.charAt(signatureStart) == 'A' ? "B" : "A") + token.substring(signatureStart + 1);
        for (String candidate : List.of("invalid", tampered)) {
            mvc.perform(get("/api/health").header("Authorization", "Bearer " + candidate))
                    .andExpect(status().isUnauthorized());
        }
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer(SecurityConfiguration.ISSUER).subject(TestCredentials.USERNAME)
                .audience(List.of(SecurityConfiguration.AUDIENCE))
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    void expiredWrongIssuerSubjectAudienceAndMissingExpiryAreRejected() throws Exception {
        for (JwtClaimsSet claims : List.of(
                claims().expiresAt(Instant.now().minusSeconds(60)).build(),
                claims().issuer("other").build(),
                claims().subject("other").build(),
                claims().audience(List.of("other")).build(),
                JwtClaimsSet.builder().issuer(SecurityConfiguration.ISSUER).subject(TestCredentials.USERNAME)
                        .audience(List.of(SecurityConfiguration.AUDIENCE)).build())) {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/health").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void invalidBodiesAndTypesAreRejectedBeforeService() throws Exception {
        String token = login();
        for (String body : List.of("{}", "{\"title\":\"   \"}", "{\"title\":\"test\",\"slotNo\":2}",
                "{\"title\":\"test\",\"categoryId\":-1}", "{\"title\":\"test\",\"status\":\"COMPLETED\"}",
                "{\"title\":\"" + "x".repeat(256) + "\"}")) {
            mvc.perform(post("/api/goals/1").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/goals/not-a-number").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/goals/1/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(categories, goals);
    }
}
