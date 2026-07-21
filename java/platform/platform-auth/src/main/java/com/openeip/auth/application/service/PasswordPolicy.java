package com.openeip.auth.application.service;

import com.openeip.auth.shared.exception.AuthException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Enforces BCrypt's character and UTF-8 byte boundaries before hashing. */
@Component
public class PasswordPolicy {

  static final int MIN_CHARACTERS = 8;
  static final int MAX_CHARACTERS = 72;
  static final int MAX_UTF8_BYTES = 72;

  public void validate(String password) {
    int characters = password == null ? 0 : password.codePointCount(0, password.length());
    int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
    if (characters < MIN_CHARACTERS || characters > MAX_CHARACTERS || bytes > MAX_UTF8_BYTES) {
      throw AuthException.validation(
          "Password must contain 8 to 72 characters and no more than 72 UTF-8 bytes");
    }
  }
}
