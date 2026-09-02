package com.openeip.governance.infrastructure.policy;

import com.openeip.governance.application.catalog.PromptContentCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-GCM Prompt encryption using an externally supplied 256-bit key. */
public final class AesGcmPromptContentCipher implements PromptContentCipher {
  private static final String VERSION = "v1";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public AesGcmPromptContentCipher(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalArgumentException("Prompt encryption key is required");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(base64Key);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Prompt encryption key must be base64", exception);
    }
    if (decoded.length != 32) {
      throw new IllegalArgumentException("Prompt encryption key must be 256-bit");
    }
    key = new SecretKeySpec(decoded, "AES");
  }

  @Override
  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank() || plaintext.length() > 65536) {
      throw new IllegalArgumentException("Prompt content is required and bounded");
    }
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] value =
          ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
      return VERSION + ":" + Base64.getEncoder().encodeToString(value);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Prompt encryption is unavailable", exception);
    }
  }

  @Override
  public String digest(String plaintext) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}
