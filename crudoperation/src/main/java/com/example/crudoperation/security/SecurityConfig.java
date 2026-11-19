package com.example.crudoperation.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(SecuritySettings.class)
public class SecurityConfig {
    public static final String ISSUER = "crudoperation";
    public static final String AUDIENCE = "crudoperation-api";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http
                // Authentication uses explicit Bearer headers, never cookies or HTTP Basic.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/students", "/students/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.HEAD, "/students", "/students/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/students", "/students/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/students", "/students/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/students", "/students/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                        .decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    InMemoryUserDetailsManager users(SecuritySettings settings, PasswordEncoder encoder) {
        if (settings.adminUsername() == null || settings.userUsername() == null
                || settings.adminUsername().equals(settings.userUsername())) {
            throw new IllegalArgumentException("Configure distinct API_ADMIN_USERNAME and API_USER_USERNAME.");
        }
        return new InMemoryUserDetailsManager(
                account(settings.adminUsername(), settings.adminPassword(), "ADMIN", encoder),
                account(settings.userUsername(), settings.userPassword(), "USER", encoder));
    }

    private UserDetails account(String username, String password, String role, PasswordEncoder encoder) {
        if (username.isBlank() || password == null || password.isBlank()
                || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("API accounts require a username and a password of at least "
                    + "12 characters and at most 72 UTF-8 bytes.");
        }
        return User.withUsername(username).password(encoder.encode(password)).roles(role).build();
    }

    @Bean
    AuthenticationManager authenticationManager(InMemoryUserDetailsManager users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecretKey jwtKey(SecuritySettings settings) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(settings.jwtSecret());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be a Base64-encoded random key.");
        }
        if (decoded.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 bytes.");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtClaimValidator<List<String>> audience = new JwtClaimValidator<>("aud",
                value -> value != null && value.contains(AUDIENCE));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), audience,
                new JwtClaimValidator<>("exp", value -> value != null)));
        return decoder;
    }
}
