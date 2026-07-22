package com.openeip.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.document.shared.exception.DocumentException;
import org.junit.jupiter.api.Test;

class FileUploadPolicyTest {

  private final FileUploadPolicy policy = new FileUploadPolicy();

  @Test
  void acceptsSupportedSuffixAndNormalizedMediaType() {
    var result = policy.validate("notes.TXT", "Text/Plain; charset=UTF-8", 4, 10);

    assertThat(result.originalName()).isEqualTo("notes.TXT");
    assertThat(result.contentType()).isEqualTo("text/plain");
  }

  @Test
  void acceptsAllMvpImageAndPdfSuffixes() {
    assertThat(policy.validate("a.pdf", "application/pdf", 1, 1).contentType())
        .isEqualTo("application/pdf");
    assertThat(policy.validate("a.png", "image/png", 1, 1).contentType()).isEqualTo("image/png");
    assertThat(policy.validate("a.jpg", "image/jpeg", 1, 1).contentType()).isEqualTo("image/jpeg");
    assertThat(policy.validate("a.jpeg", "image/jpeg", 1, 1).contentType()).isEqualTo("image/jpeg");
  }

  @Test
  void rejectsUnsafeOrMissingFilename() {
    for (String name : new String[] {null, "", "../secret.txt", "a\\b.txt", "bad\u0000.txt", "."}) {
      assertThatThrownBy(() -> policy.validate(name, "text/plain", 1, 10))
          .isInstanceOf(DocumentException.class)
          .extracting("errorCode")
          .isEqualTo("DOC-V-001");
    }
    assertThatThrownBy(() -> policy.validate("a".repeat(252) + ".txt", "text/plain", 1, 10))
        .isInstanceOf(DocumentException.class);
  }

  @Test
  void rejectsEmptyAndOversizedContent() {
    assertThatThrownBy(() -> policy.validate("a.txt", "text/plain", 0, 10))
        .isInstanceOf(DocumentException.class)
        .extracting("errorCode")
        .isEqualTo("DOC-V-001");
    assertThatThrownBy(() -> policy.validate("a.txt", "text/plain", 11, 10))
        .isInstanceOf(DocumentException.class)
        .extracting("errorCode")
        .isEqualTo("DOC-V-003");
  }

  @Test
  void rejectsUnsupportedOrMismatchedTypes() {
    for (String[] value :
        new String[][] {
          {"a.exe", "application/octet-stream"},
          {"a.txt", "application/pdf"},
          {"a.txt", null},
          {"a.txt", "not a media type"},
          {"no-suffix", "text/plain"}
        }) {
      assertThatThrownBy(() -> policy.validate(value[0], value[1], 1, 10))
          .isInstanceOf(DocumentException.class)
          .extracting("errorCode")
          .isEqualTo("DOC-V-002");
    }
  }
}
