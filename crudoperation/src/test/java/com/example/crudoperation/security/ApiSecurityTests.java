package com.example.crudoperation.security;

import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.example.crudoperation.repository.StudentRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired StudentRepository repository;
    @Autowired JwtEncoder encoder;
    @Autowired InMemoryUserDetailsManager users;
    @Autowired PasswordEncoder passwordEncoder;
    private static final String STUDENT = """
            {"name":"Sai","email":"sai@example.com","course":"Java"}
            """;

    @BeforeEach
    void resetStudents() {
        repository.deleteAll();
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String sign(JwtEncoder signer, String issuer, String audience, Instant expiry) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject("test-admin")
                .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(1200))
                .claim("roles", List.of("ADMIN"));
        if (expiry != null) claims.expiresAt(expiry);
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
    }

    @Test
    void anonymousRequestsCannotReadOrModifyStudents() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(get("/students"), get("/students/1"),
                post("/students").contentType(MediaType.APPLICATION_JSON).content(STUDENT),
                put("/students/1").contentType(MediaType.APPLICATION_JSON).content(STUDENT),
                delete("/students/1"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        assertEquals(0, repository.count());
    }

    @Test
    void loginRejectsWrongCredentialsAndMissingFields() throws Exception {
        for (String username : List.of("test-admin", "unknown")) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"" + "é".repeat(40) + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readerCanReadButCannotWrite() throws Exception {
        String token = login("test-reader", "Reader-test-password-42");
        mvc.perform(get("/students").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        for (MockHttpServletRequestBuilder request : List.of(
                post("/students").contentType(MediaType.APPLICATION_JSON).content(STUDENT),
                put("/students/1").contentType(MediaType.APPLICATION_JSON).content(STUDENT), delete("/students/1"))) {
            mvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        assertEquals(0, repository.count());
    }

    @Test
    void adminTokenCanCompleteCrudWithoutCookiesOrCsrfTokens() throws Exception {
        String token = login("test-admin", "Admin-test-password-42");
        String authorization = "Bearer " + token;
        String location = mvc.perform(post("/students").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        assertNotNull(location);
        mvc.perform(get(location).header("Authorization", authorization)).andExpect(status().isOk());
        mvc.perform(put(location).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content(STUDENT.replace("Java", "Spring")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.course").value("Spring"));
        mvc.perform(delete(location).header("Authorization", authorization)).andExpect(status().isNoContent());
        mvc.perform(get(location).header("Authorization", authorization)).andExpect(status().isNotFound());
        assertEquals(0, repository.count());
    }

    @Test
    void rejectsInvalidExpiredOrMisissuedTokens() throws Exception {
        JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(new byte[32], "HmacSHA256")));
        Instant future = Instant.now().plusSeconds(900);
        for (String token : List.of("not-a-token",
                sign(otherKey, SecurityConfig.ISSUER, SecurityConfig.AUDIENCE, future),
                sign(encoder, SecurityConfig.ISSUER, SecurityConfig.AUDIENCE, Instant.now().minusSeconds(120)),
                sign(encoder, "wrong-issuer", SecurityConfig.AUDIENCE, future),
                sign(encoder, SecurityConfig.ISSUER, "wrong-audience", future),
                sign(encoder, SecurityConfig.ISSUER, SecurityConfig.AUDIENCE, null))) {
            mvc.perform(get("/students").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void doesNotAcceptBasicAuthOrCreateAnAuthenticatedSession() throws Exception {
        login("test-admin", "Admin-test-password-42");
        mvc.perform(get("/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/students").with(httpBasic("test-admin", "Admin-test-password-42")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storesPasswordsAsHashesAndRejectsWeakSigningConfiguration() {
        String hash = users.loadUserByUsername("test-admin").getPassword();
        assertNotEquals("Admin-test-password-42", hash);
        assertTrue(passwordEncoder.matches("Admin-test-password-42", hash));
        SecurityConfig configuration = new SecurityConfig();
        for (String key : List.of("not base64!", "c2hvcnQ=")) {
            assertThrows(IllegalArgumentException.class, () -> configuration.jwtKey(
                    new SecuritySettings(key, "admin", "adminpassword", "reader", "readerpassword")));
        }
    }
}
