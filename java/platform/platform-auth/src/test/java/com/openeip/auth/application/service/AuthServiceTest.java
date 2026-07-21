package com.openeip.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.auth.domain.entity.Permission;
import com.openeip.auth.domain.entity.RefreshToken;
import com.openeip.auth.domain.entity.Role;
import com.openeip.auth.domain.entity.User;
import com.openeip.auth.domain.repository.PermissionRepository;
import com.openeip.auth.domain.repository.RefreshTokenRepository;
import com.openeip.auth.domain.repository.RoleRepository;
import com.openeip.auth.domain.repository.UserRepository;
import com.openeip.auth.shared.exception.AuthException;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private JwtService jwtService;
  @Mock private Claims claims;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
  private AuthService authService;
  private Role userRole;
  private User user;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            userRepository,
            roleRepository,
            permissionRepository,
            refreshTokenRepository,
            passwordEncoder,
            new PasswordPolicy(),
            jwtService);
    userRole = Role.builder().id("role-user").name("ROLE_USER").build();
    user =
        User.builder()
            .id("user-1")
            .username("testuser")
            .email("test@openeip.org")
            .password(passwordEncoder.encode("password123"))
            .roles(Set.of(userRole))
            .build();
  }

  @Test
  void registersUserWithDefaultRole() {
    when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User registered = authService.register("newuser", "new@openeip.org", "password123");

    assertThat(registered.getUsername()).isEqualTo("newuser");
    assertThat(registered.getRoles()).containsExactly(userRole);
    assertThat(passwordEncoder.matches("password123", registered.getPassword())).isTrue();
  }

  @Test
  void rejectsDuplicateUsernameAndEmail() {
    when(userRepository.existsByUsername("testuser")).thenReturn(true);
    assertCode(
        () -> authService.register("testuser", "new@openeip.org", "password123"), "AUTH-E-004");

    when(userRepository.existsByUsername("other")).thenReturn(false);
    when(userRepository.existsByEmail("test@openeip.org")).thenReturn(true);
    assertCode(
        () -> authService.register("other", "test@openeip.org", "password123"), "AUTH-E-004");
  }

  @Test
  void loginIssuesAndPersistsTokenPair() {
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
    stubIssuedTokens();

    AuthService.TokenPair pair = authService.login("testuser", "password123");

    assertThat(pair.accessToken()).isEqualTo("access-token");
    assertThat(pair.refreshToken()).isEqualTo("refresh-token");
    verify(refreshTokenRepository).save(any(RefreshToken.class));
  }

  @Test
  void loginRejectsUnknownWrongPasswordAndDisabledUser() {
    assertCode(() -> authService.login("missing", "password123"), "AUTH-E-001");

    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
    assertCode(() -> authService.login("testuser", "wrong-password"), "AUTH-E-001");

    user.setActive(false);
    assertCode(() -> authService.login("testuser", "password123"), "AUTH-E-001");
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  void refreshConsumesCurrentTokenAndRotatesIt() {
    RefreshToken current =
        RefreshToken.builder()
            .user(user)
            .tokenHash(AuthService.hashTokenId("old-jti"))
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    when(jwtService.parseRefreshToken("encoded-refresh")).thenReturn(claims);
    when(claims.getId()).thenReturn("old-jti");
    when(claims.getSubject()).thenReturn("user-1");
    when(refreshTokenRepository.findByTokenHashForUpdate(current.getTokenHash()))
        .thenReturn(Optional.of(current));
    stubIssuedTokens();

    AuthService.TokenPair pair = authService.refresh("encoded-refresh");

    assertThat(pair.refreshToken()).isEqualTo("refresh-token");
    assertThat(current.getUsedAt()).isNotNull();
    verify(refreshTokenRepository).save(current);
  }

  @Test
  void refreshRejectsReplayDisabledUserAndSubjectMismatch() {
    RefreshToken current =
        RefreshToken.builder()
            .user(user)
            .tokenHash(AuthService.hashTokenId("old-jti"))
            .expiresAt(Instant.now().plusSeconds(60))
            .usedAt(Instant.now())
            .build();
    when(jwtService.parseRefreshToken("encoded-refresh")).thenReturn(claims);
    when(claims.getId()).thenReturn("old-jti");
    when(refreshTokenRepository.findByTokenHashForUpdate(current.getTokenHash()))
        .thenReturn(Optional.of(current));

    assertCode(() -> authService.refresh("encoded-refresh"), "AUTH-E-003");

    current.setUsedAt(null);
    user.setActive(false);
    when(claims.getSubject()).thenReturn("user-1");
    assertCode(() -> authService.refresh("encoded-refresh"), "AUTH-E-003");

    user.setActive(true);
    when(claims.getSubject()).thenReturn("different-user");
    assertCode(() -> authService.refresh("encoded-refresh"), "AUTH-E-003");
  }

  @Test
  void getsOnlyActiveUsers() {
    when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
    assertThat(authService.getActiveUser("user-1")).isSameAs(user);

    user.setActive(false);
    assertCode(() -> authService.getActiveUser("user-1"), "AUTH-E-003");
    assertCode(() -> authService.getActiveUser("missing"), "AUTH-E-003");
  }

  @Test
  void createsRoleFromKnownPermissions() {
    Permission permission = Permission.builder().id("permission-1").code("document:read").build();
    when(permissionRepository.findAllByCodeIn(Set.of("document:read")))
        .thenReturn(List.of(permission));
    when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Role role = authService.createRole("ROLE_EDITOR", "Editor", Set.of("document:read"));

    assertThat(role.getName()).isEqualTo("ROLE_EDITOR");
    assertThat(role.getPermissions()).containsExactly(permission);
  }

  @Test
  void rejectsDuplicateRoleAndUnknownPermission() {
    when(roleRepository.existsByName("ROLE_USER")).thenReturn(true);
    assertCode(() -> authService.createRole("ROLE_USER", "Duplicate", Set.of()), "AUTH-E-006");

    assertCode(() -> authService.createRole("ROLE_NEW", "New", Set.of("missing")), "AUTH-E-005");
  }

  @Test
  void replacesUserRolesAndRejectsMissingResources() {
    Role admin = Role.builder().id("role-admin").name("ROLE_ADMIN").build();
    when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
    when(roleRepository.findAllByNameIn(Set.of("ROLE_ADMIN"))).thenReturn(List.of(admin));
    when(userRepository.save(user)).thenReturn(user);

    User updated = authService.replaceUserRoles("user-1", Set.of("ROLE_ADMIN"));
    assertThat(updated.getRoles()).containsExactly(admin);

    assertCode(() -> authService.replaceUserRoles("missing", Set.of("ROLE_ADMIN")), "AUTH-E-005");
    assertCode(() -> authService.replaceUserRoles("user-1", Set.of("ROLE_UNKNOWN")), "AUTH-E-005");
  }

  private void stubIssuedTokens() {
    when(jwtService.generateAccessToken("user-1"))
        .thenReturn(
            new JwtService.TokenValue("access-token", "access-jti", Instant.now().plusSeconds(60)));
    when(jwtService.generateRefreshToken("user-1"))
        .thenReturn(
            new JwtService.TokenValue(
                "refresh-token", "refresh-jti", Instant.now().plusSeconds(120)));
    when(jwtService.getAccessExpiration()).thenReturn(60L);
  }

  private static void assertCode(ThrowingAction action, String expectedCode) {
    assertThatThrownBy(action::run)
        .isInstanceOf(AuthException.class)
        .extracting("errorCode")
        .isEqualTo(expectedCode);
  }

  @FunctionalInterface
  private interface ThrowingAction {
    void run();
  }
}
