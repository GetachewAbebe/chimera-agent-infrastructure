package org.chimera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiKeyAuthServiceTest {

  @Test
  void shouldParseValidApiKeyConfiguration() {
    Map<String, ApiKeyIdentity> parsed =
        ApiKeyAuthService.parse("key-a:tenant-alpha:operator,viewer;key-b:tenant-beta:reviewer");

    assertThat(parsed).hasSize(2);
    assertThat(parsed.get("key-a").tenantId()).isEqualTo("tenant-alpha");
    assertThat(parsed.get("key-a").allowedRoles())
        .containsExactlyInAnyOrder(UserRole.OPERATOR, UserRole.VIEWER);
    assertThat(parsed.get("key-b").allowedRoles()).containsExactly(UserRole.REVIEWER);
  }

  @Test
  void shouldRejectInvalidApiKeyConfigurationEntry() {
    assertThatThrownBy(() -> ApiKeyAuthService.parse("broken-entry"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid CHIMERA_API_KEYS entry");
  }

  @Test
  void shouldAuthenticateKnownApiKey() {
    ApiKeyAuthService authService =
        new ApiKeyAuthService(
            Map.of(
                "known-key",
                new ApiKeyIdentity("tenant-alpha", Set.of(UserRole.OPERATOR, UserRole.VIEWER))));

    assertThat(authService.authenticate("known-key")).isPresent();
    assertThat(authService.authenticate("missing-key")).isEmpty();
    assertThat(authService.authenticate(null)).isEmpty();
  }
}
