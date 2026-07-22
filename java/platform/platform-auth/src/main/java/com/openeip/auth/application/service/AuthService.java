package com.openeip.auth.application.service;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authentication, refresh rotation, and RBAC application service. */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final JwtService jwtService;

  public AuthService(
      UserRepository userRepository,
      RoleRepository roleRepository,
      PermissionRepository permissionRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.jwtService = jwtService;
  }

  @Transactional
  public User register(String username, String email, String password) {
    passwordPolicy.validate(password);
    if (userRepository.existsByUsername(username)) {
      throw AuthException.duplicateUsername(username);
    }
    if (userRepository.existsByEmail(email)) {
      throw AuthException.duplicateEmail(email);
    }

    Role defaultRole =
        roleRepository
            .findByName("ROLE_USER")
            .orElseThrow(() -> new IllegalStateException("Default role ROLE_USER not found"));
    User user =
        User.builder()
            .username(username)
            .email(email)
            .password(passwordEncoder.encode(password))
            .roles(new HashSet<>(Set.of(defaultRole)))
            .build();
    return userRepository.save(user);
  }

  @Transactional
  public TokenPair login(String username, String password) {
    User user =
        userRepository.findByUsername(username).orElseThrow(AuthException::invalidCredentials);
    if (!user.isActive() || !passwordEncoder.matches(password, user.getPassword())) {
      throw AuthException.invalidCredentials();
    }
    return issueTokenPair(user);
  }

  @Transactional
  public TokenPair refresh(String encodedRefreshToken) {
    Claims claims = jwtService.parseRefreshToken(encodedRefreshToken);
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHashForUpdate(hashTokenId(claims.getId()))
            .orElseThrow(AuthException::tokenInvalid);
    Instant now = Instant.now();
    User user = current.getUser();
    if (!current.isConsumable(now)
        || !current.getUser().getId().equals(claims.getSubject())
        || !user.isActive()
        || user.getDeletedAt() != null) {
      throw AuthException.tokenInvalid();
    }

    current.setUsedAt(now);
    refreshTokenRepository.save(current);
    return issueTokenPair(user);
  }

  @Transactional(readOnly = true)
  public User getActiveUser(String userId) {
    User user = userRepository.findById(userId).orElseThrow(AuthException::tokenInvalid);
    if (!user.isActive() || user.getDeletedAt() != null) {
      throw AuthException.tokenInvalid();
    }
    return user;
  }

  @Transactional(readOnly = true)
  public List<Role> listRoles() {
    return roleRepository.findAll();
  }

  @Transactional(readOnly = true)
  public Page<User> listUsers(int page, int pageSize) {
    return userRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page - 1, pageSize));
  }

  @Transactional
  public User setUserActive(String actorId, String userId, boolean active) {
    if (actorId.equals(userId) && !active) {
      throw AuthException.validation("Administrators cannot disable their own account");
    }
    User user = userRepository.findById(userId).orElseThrow(() -> AuthException.notFound("User"));
    user.setActive(active);
    return userRepository.save(user);
  }

  @Transactional
  public Role createRole(String name, String description, Set<String> permissionCodes) {
    if (roleRepository.existsByName(name)) {
      throw AuthException.duplicateRole(name);
    }
    List<Permission> permissions = permissionRepository.findAllByCodeIn(permissionCodes);
    Set<String> found = permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
    if (!found.equals(permissionCodes)) {
      throw AuthException.notFound("One or more permissions");
    }
    return roleRepository.save(
        Role.builder()
            .name(name)
            .description(description)
            .permissions(new HashSet<>(permissions))
            .build());
  }

  @Transactional
  public User replaceUserRoles(String userId, Set<String> roleNames) {
    User user = userRepository.findById(userId).orElseThrow(() -> AuthException.notFound("User"));
    List<Role> roles = roleRepository.findAllByNameIn(roleNames);
    Set<String> found = roles.stream().map(Role::getName).collect(Collectors.toSet());
    if (!found.equals(roleNames)) {
      throw AuthException.notFound("One or more roles");
    }
    user.setRoles(new HashSet<>(roles));
    return userRepository.save(user);
  }

  private TokenPair issueTokenPair(User user) {
    JwtService.TokenValue access = jwtService.generateAccessToken(user.getId());
    JwtService.TokenValue refresh = jwtService.generateRefreshToken(user.getId());
    refreshTokenRepository.save(
        RefreshToken.builder()
            .user(user)
            .tokenHash(hashTokenId(refresh.tokenId()))
            .expiresAt(refresh.expiresAt())
            .build());
    return new TokenPair(
        access.value(), refresh.value(), jwtService.getAccessExpiration(), "Bearer");
  }

  static String hashTokenId(String tokenId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(tokenId.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record TokenPair(
      String accessToken, String refreshToken, long expiresIn, String tokenType) {}
}
