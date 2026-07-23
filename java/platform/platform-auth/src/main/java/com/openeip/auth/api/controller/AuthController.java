package com.openeip.auth.api.controller;

import com.openeip.auth.api.dto.request.AssignRolesRequest;
import com.openeip.auth.api.dto.request.CreateRoleRequest;
import com.openeip.auth.api.dto.request.LoginRequest;
import com.openeip.auth.api.dto.request.RefreshRequest;
import com.openeip.auth.api.dto.request.RegisterRequest;
import com.openeip.auth.api.dto.request.UpdateUserStatusRequest;
import com.openeip.auth.api.dto.response.RoleResponse;
import com.openeip.auth.api.dto.response.TokenResponse;
import com.openeip.auth.api.dto.response.UserAdminResponse;
import com.openeip.auth.api.dto.response.UserInfoResponse;
import com.openeip.auth.api.dto.response.UserPageResponse;
import com.openeip.auth.application.service.AuthService;
import com.openeip.auth.domain.entity.Role;
import com.openeip.auth.domain.entity.User;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authentication and RBAC REST API. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "AuthService is an application-scoped Spring dependency.")
  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiEnvelope<UserInfoResponse>> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    User user = authService.register(request.username(), request.email(), request.password());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiEnvelope.success(UserInfoResponse.from(user), RequestIdFilter.get(servletRequest)));
  }

  @PostMapping("/login")
  public ApiEnvelope<TokenResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    return ApiEnvelope.success(
        TokenResponse.from(authService.login(request.username(), request.password())),
        RequestIdFilter.get(servletRequest));
  }

  @PostMapping("/refresh")
  public ApiEnvelope<TokenResponse> refresh(
      @Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
    return ApiEnvelope.success(
        TokenResponse.from(authService.refresh(request.refreshToken())),
        RequestIdFilter.get(servletRequest));
  }

  @GetMapping("/me")
  public ApiEnvelope<UserInfoResponse> me(
      Authentication authentication, HttpServletRequest servletRequest) {
    User user = authService.getActiveUser(authentication.getName());
    return ApiEnvelope.success(UserInfoResponse.from(user), RequestIdFilter.get(servletRequest));
  }

  @GetMapping("/roles")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ApiEnvelope<List<RoleResponse>> listRoles(HttpServletRequest servletRequest) {
    List<RoleResponse> roles = authService.listRoles().stream().map(RoleResponse::from).toList();
    return ApiEnvelope.success(roles, RequestIdFilter.get(servletRequest));
  }

  @GetMapping("/users")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ApiEnvelope<UserPageResponse> listUsers(
      @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
      HttpServletRequest servletRequest) {
    var users = authService.listUsers(page, pageSize).map(UserAdminResponse::from);
    return ApiEnvelope.success(UserPageResponse.from(users), RequestIdFilter.get(servletRequest));
  }

  @PatchMapping("/users/{id}/active")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ApiEnvelope<UserAdminResponse> setUserActive(
      @PathVariable String id,
      @Valid @RequestBody UpdateUserStatusRequest request,
      Authentication authentication,
      HttpServletRequest servletRequest) {
    User user = authService.setUserActive(authentication.getName(), id, request.active());
    return ApiEnvelope.success(UserAdminResponse.from(user), RequestIdFilter.get(servletRequest));
  }

  @PostMapping("/roles")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<ApiEnvelope<RoleResponse>> createRole(
      @Valid @RequestBody CreateRoleRequest request, HttpServletRequest servletRequest) {
    Role role =
        authService.createRole(request.name(), request.description(), request.permissionCodes());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiEnvelope.success(RoleResponse.from(role), RequestIdFilter.get(servletRequest)));
  }

  @PutMapping("/users/{id}/roles")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ApiEnvelope<UserInfoResponse> replaceUserRoles(
      @PathVariable String id,
      @Valid @RequestBody AssignRolesRequest request,
      HttpServletRequest servletRequest) {
    User user = authService.replaceUserRoles(id, request.roleNames());
    return ApiEnvelope.success(UserInfoResponse.from(user), RequestIdFilter.get(servletRequest));
  }
}
