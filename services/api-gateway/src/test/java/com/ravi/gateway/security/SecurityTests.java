package com.ravi.gateway.security;

import com.ravi.gateway.infrastructure.security.KeycloakJwtAuthenticationConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityTests {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    @DisplayName("Keycloak Converter: extracts realm roles and prefixes with ROLE_")
    void testExtractRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "user-uuid-12345")
                .claim("preferred_username", "customer1")
                .claim("email", "customer1@example.com")
                .claim("realm_access", Map.of(
                        "roles", List.of("CUSTOMER", "ROLE_ADMIN")
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token).isNotNull();
        assertThat(token.getName()).isEqualTo("customer1");

        List<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Keycloak Converter: falls back to subject claim when preferred_username is missing")
    void testFallbackToSubject() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "user-sub-999")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token).isNotNull();
        assertThat(token.getName()).isEqualTo("user-sub-999");
    }
}
