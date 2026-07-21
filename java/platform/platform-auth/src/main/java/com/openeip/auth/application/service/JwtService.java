package com.openeip.auth.application.service;

import com.openeip.auth.shared.exception.AuthException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Issues and verifies strictly typed RS256 access and refresh tokens. */
@Service
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Fail-closed key and expiration validation must abort construction.")
public class JwtService {

  static final String ISSUER = "openeip";
  static final String ACCESS_TYPE = "access";
  static final String REFRESH_TYPE = "refresh";

  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private final long accessExpiration;
  private final long refreshExpiration;

  public JwtService(
      @Value("${openeip.jwt.private-key-base64:}") String privateKeyBase64,
      @Value("${openeip.jwt.public-key-base64:}") String publicKeyBase64,
      @Value("${openeip.jwt.allow-ephemeral-key:false}") boolean allowEphemeralKey,
      @Value("${openeip.jwt.access-expiration:3600}") long accessExpiration,
      @Value("${openeip.jwt.refresh-expiration:604800}") long refreshExpiration) {
    KeyPair pair = loadKeyPair(privateKeyBase64, publicKeyBase64, allowEphemeralKey);
    this.privateKey = pair.getPrivate();
    this.publicKey = pair.getPublic();
    this.accessExpiration = requirePositive(accessExpiration, "access expiration");
    this.refreshExpiration = requirePositive(refreshExpiration, "refresh expiration");
  }

  public TokenValue generateAccessToken(String userId) {
    return generateToken(userId, ACCESS_TYPE, accessExpiration);
  }

  public TokenValue generateRefreshToken(String userId) {
    return generateToken(userId, REFRESH_TYPE, refreshExpiration);
  }

  public Claims parseAccessToken(String token) {
    return parseRequiredType(token, ACCESS_TYPE);
  }

  public Claims parseRefreshToken(String token) {
    return parseRequiredType(token, REFRESH_TYPE);
  }

  public long getAccessExpiration() {
    return accessExpiration;
  }

  private TokenValue generateToken(String userId, String type, long lifetimeSeconds) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(lifetimeSeconds);
    String tokenId = UUID.randomUUID().toString();
    String token =
        Jwts.builder()
            .issuer(ISSUER)
            .subject(userId)
            .claim("type", type)
            .id(tokenId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    return new TokenValue(token, tokenId, expiresAt);
  }

  private Claims parseRequiredType(String token, String requiredType) {
    try {
      var signedClaims =
          Jwts.parser()
              .verifyWith(publicKey)
              .requireIssuer(ISSUER)
              .require("type", requiredType)
              .build()
              .parseSignedClaims(token);
      if (!Jwts.SIG.RS256.getId().equals(signedClaims.getHeader().getAlgorithm())) {
        throw AuthException.tokenInvalid();
      }
      Claims claims = signedClaims.getPayload();
      if (!StringUtils.hasText(claims.getSubject()) || !StringUtils.hasText(claims.getId())) {
        throw AuthException.tokenInvalid();
      }
      return claims;
    } catch (ExpiredJwtException exception) {
      throw AuthException.tokenExpired();
    } catch (AuthException exception) {
      throw exception;
    } catch (JwtException | IllegalArgumentException exception) {
      throw AuthException.tokenInvalid();
    }
  }

  private static KeyPair loadKeyPair(
      String privateKeyBase64, String publicKeyBase64, boolean allowEphemeralKey) {
    boolean hasPrivate = StringUtils.hasText(privateKeyBase64);
    boolean hasPublic = StringUtils.hasText(publicKeyBase64);
    if (!hasPrivate && !hasPublic && allowEphemeralKey) {
      return generateKeyPair();
    }
    if (!hasPrivate || !hasPublic) {
      throw new IllegalStateException(
          "Both JWT_PRIVATE_KEY_BASE64 and JWT_PUBLIC_KEY_BASE64 are required");
    }

    try {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      PrivateKey privateKey =
          factory.generatePrivate(
              new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
      PublicKey publicKey =
          factory.generatePublic(
              new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
      validateKeyPair(privateKey, publicKey);
      return new KeyPair(publicKey, privateKey);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("Invalid JWT RSA key configuration", exception);
    }
  }

  private static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to generate ephemeral RSA key pair", exception);
    }
  }

  private static void validateKeyPair(PrivateKey privateKey, PublicKey publicKey)
      throws GeneralSecurityException {
    if (!(privateKey instanceof RSAPrivateKey rsaPrivate)
        || !(publicKey instanceof RSAPublicKey rsaPublic)
        || rsaPrivate.getModulus().bitLength() < 2048
        || rsaPublic.getModulus().bitLength() < 2048) {
      throw new GeneralSecurityException("RSA keys must have a modulus of at least 2048 bits");
    }

    byte[] challenge = "openeip-jwt-key-check".getBytes(StandardCharsets.US_ASCII);
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(challenge);
    byte[] signed = signature.sign();
    signature.initVerify(publicKey);
    signature.update(challenge);
    if (!signature.verify(signed)) {
      throw new GeneralSecurityException("JWT private and public keys do not match");
    }
  }

  private static long requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  public record TokenValue(String value, String tokenId, Instant expiresAt) {}
}
