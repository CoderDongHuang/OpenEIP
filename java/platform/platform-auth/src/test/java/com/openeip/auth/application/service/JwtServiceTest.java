package com.openeip.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.auth.shared.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  @Test
  void signsAndVerifiesTypedRs256Tokens() {
    JwtService service = ephemeralService(3600);

    JwtService.TokenValue access = service.generateAccessToken("user-1");
    JwtService.TokenValue refresh = service.generateRefreshToken("user-1");

    Claims accessClaims = service.parseAccessToken(access.value());
    Claims refreshClaims = service.parseRefreshToken(refresh.value());
    assertThat(accessClaims.getSubject()).isEqualTo("user-1");
    assertThat(accessClaims.get("type")).isEqualTo("access");
    assertThat(accessClaims.getId()).isEqualTo(access.tokenId());
    assertThat(refreshClaims.get("type")).isEqualTo("refresh");
    assertThat(refreshClaims.getId()).isEqualTo(refresh.tokenId());
    assertThat(accessClaims.getIssuedAt()).isNotNull();
    assertThat(accessClaims.getExpiration()).isNotNull();
  }

  @Test
  void rejectsWrongTokenTypeAndWrongKey() {
    JwtService first = ephemeralService(3600);
    JwtService second = ephemeralService(3600);
    String access = first.generateAccessToken("user-1").value();

    assertThatThrownBy(() -> first.parseRefreshToken(access))
        .isInstanceOf(AuthException.class)
        .extracting("errorCode")
        .isEqualTo("AUTH-E-003");
    assertThatThrownBy(() -> second.parseAccessToken(access))
        .isInstanceOf(AuthException.class)
        .extracting("errorCode")
        .isEqualTo("AUTH-E-003");
  }

  @Test
  void rejectsExpiredAndMalformedTokens() throws InterruptedException {
    JwtService service = ephemeralService(1);
    String token = service.generateAccessToken("user-1").value();
    Thread.sleep(1200);

    assertThatThrownBy(() -> service.parseAccessToken(token))
        .isInstanceOf(AuthException.class)
        .extracting("errorCode")
        .isEqualTo("AUTH-E-002");
    assertThatThrownBy(() -> service.parseAccessToken("not-a-jwt"))
        .isInstanceOf(AuthException.class)
        .extracting("errorCode")
        .isEqualTo("AUTH-E-003");
  }

  @Test
  void productionConfigurationFailsClosed() {
    assertThatThrownBy(() -> new JwtService("", "", false, 60, 120))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("required");
    assertThatThrownBy(() -> new JwtService("invalid", "invalid", false, 60, 120))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid JWT RSA key");
  }

  @Test
  void rejectsWeakAndMismatchedConfiguredKeys() throws Exception {
    KeyPair weak = keyPair(1024);
    assertThatThrownBy(
            () ->
                new JwtService(
                    encoded(weak.getPrivate().getEncoded()),
                    encoded(weak.getPublic().getEncoded()),
                    false,
                    60,
                    120))
        .isInstanceOf(IllegalStateException.class);

    KeyPair first = keyPair(2048);
    KeyPair second = keyPair(2048);
    assertThatThrownBy(
            () ->
                new JwtService(
                    encoded(first.getPrivate().getEncoded()),
                    encoded(second.getPublic().getEncoded()),
                    false,
                    60,
                    120))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsNonRs256AlgorithmEvenWhenSignatureKeyMatches() throws Exception {
    KeyPair pair = keyPair(2048);
    JwtService service =
        new JwtService(
            encoded(pair.getPrivate().getEncoded()),
            encoded(pair.getPublic().getEncoded()),
            false,
            60,
            120);
    Instant now = Instant.now();
    String rs512Token =
        Jwts.builder()
            .issuer("openeip")
            .subject("user-1")
            .claim("type", "access")
            .id("non-rs256")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60)))
            .signWith(pair.getPrivate(), Jwts.SIG.RS512)
            .compact();

    assertThatThrownBy(() -> service.parseAccessToken(rs512Token))
        .isInstanceOf(AuthException.class)
        .extracting("errorCode")
        .isEqualTo("AUTH-E-003");
  }

  @Test
  void rejectsNonPositiveExpiration() {
    assertThatThrownBy(() -> new JwtService("", "", true, 0, 120))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtService("", "", true, 60, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static JwtService ephemeralService(long accessExpiration) {
    return new JwtService("", "", true, accessExpiration, 604800);
  }

  private static KeyPair keyPair(int bits) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    return generator.generateKeyPair();
  }

  private static String encoded(byte[] value) {
    return Base64.getEncoder().encodeToString(value);
  }
}
