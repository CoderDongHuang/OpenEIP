package com.openeip.marketplace.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.marketplace.application.MarketplaceService;
import com.openeip.marketplace.domain.PackageState;
import com.openeip.marketplace.domain.PackageType;
import com.openeip.marketplace.shared.MarketplaceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/marketplace")
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Injected Spring service is an application-scoped collaborator.")
public class MarketplaceController {
  private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
  private final MarketplaceService service;

  public MarketplaceController(MarketplaceService service) {
    this.service = service;
  }

  @GetMapping("/public/packages")
  public ApiEnvelope<MarketplaceDtos.Page> publicPackages(
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    return success(new MarketplaceDtos.Page(service.publicList(type(type), limit), null), request);
  }

  @GetMapping("/packages")
  public ApiEnvelope<MarketplaceDtos.Page> packages(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String state,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    return success(
        new MarketplaceDtos.Page(service.list(type(type), state(state), limit), null), request);
  }

  @PostMapping("/packages")
  public ResponseEntity<ApiEnvelope<Object>> create(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody MarketplaceDtos.CreatePackageRequest body,
      HttpServletRequest request) {
    requireKey(key);
    PackageType type = type(body.type());
    var value = service.create(type, body.slug(), body.displayName(), body.description());
    return response(201, value.revision(), value, request);
  }

  @GetMapping("/packages/{packageId}/versions")
  public ApiEnvelope<MarketplaceDtos.Page> versions(
      @PathVariable UUID packageId,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    return success(new MarketplaceDtos.Page(service.versions(packageId, limit), null), request);
  }

  @PostMapping("/packages/{packageId}/versions")
  public ResponseEntity<ApiEnvelope<Object>> addVersion(
      @PathVariable UUID packageId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody MarketplaceDtos.CreateVersionRequest body,
      HttpServletRequest request) {
    requireKey(key);
    var value =
        service.addVersion(
            packageId,
            body.version(),
            body.artifactUri(),
            body.sha256(),
            body.runtime(),
            body.manifest());
    return response(201, value.revision(), value, request);
  }

  @PostMapping("/packages/{packageId}/versions/{versionId}:review")
  public ResponseEntity<ApiEnvelope<Object>> review(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String revision,
      @Valid @RequestBody MarketplaceDtos.ReviewRequest body,
      HttpServletRequest request) {
    requireKey(key);
    var value = service.review(packageId, versionId, revision(revision), body.note());
    return response(200, value.revision(), value, request);
  }

  @PostMapping("/packages/{packageId}/versions/{versionId}:publish")
  public ResponseEntity<ApiEnvelope<Object>> publish(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String revision,
      HttpServletRequest request) {
    requireKey(key);
    var value = service.publish(packageId, versionId, revision(revision));
    return response(200, value.revision(), value, request);
  }

  @PostMapping("/packages/{packageId}/versions/{versionId}:suspend")
  public ResponseEntity<ApiEnvelope<Object>> suspend(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String revision,
      HttpServletRequest request) {
    requireKey(key);
    var value = service.suspend(packageId, versionId, revision(revision));
    return response(200, value.revision(), value, request);
  }

  private static PackageType type(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return PackageType.valueOf(value.toUpperCase());
    } catch (RuntimeException exception) {
      throw MarketplaceException.invalid("Package type is invalid");
    }
  }

  private static PackageState state(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return PackageState.valueOf(value.toUpperCase());
    } catch (RuntimeException exception) {
      throw MarketplaceException.invalid("Package state is invalid");
    }
  }

  private static void requireKey(String key) {
    if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
      throw MarketplaceException.invalid("Idempotency-Key is invalid");
    }
  }

  private static long revision(String value) {
    try {
      long revision = Long.parseLong(value == null ? "" : value.replace("\"", ""));
      if (revision < 0) {
        throw new NumberFormatException("negative revision");
      }
      return revision;
    } catch (RuntimeException exception) {
      throw MarketplaceException.invalid("If-Match revision is invalid");
    }
  }

  private static <T> ApiEnvelope<T> success(T value, HttpServletRequest request) {
    return ApiEnvelope.success(value, RequestIdFilter.get(request));
  }

  private static ResponseEntity<ApiEnvelope<Object>> response(
      int status, long revision, Object value, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .eTag(Long.toString(revision))
        .body(success(value, request));
  }
}
