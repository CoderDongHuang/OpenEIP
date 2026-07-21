package com.openeip.auth.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.auth.shared.exception.AuthException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy();

  @Test
  void acceptsEightThroughSeventyTwoAsciiBytes() {
    assertThatCode(() -> policy.validate("12345678")).doesNotThrowAnyException();
    assertThatCode(() -> policy.validate("a".repeat(72))).doesNotThrowAnyException();
  }

  @Test
  void rejectsCharacterAndUtf8ByteBoundaryViolations() {
    assertInvalid(null);
    assertInvalid("1234567");
    assertInvalid("a".repeat(73));
    assertInvalid("password" + "界".repeat(22));
  }

  private void assertInvalid(String password) {
    assertThatThrownBy(() -> policy.validate(password))
        .isInstanceOf(AuthException.class)
        .extracting("errorCode")
        .isEqualTo("AUTH-V-001");
  }
}
