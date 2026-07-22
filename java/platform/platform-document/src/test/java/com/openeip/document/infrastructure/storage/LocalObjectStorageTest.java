package com.openeip.document.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.document.shared.exception.DocumentException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageTest {

  private static final String KEY = "12/12345678-1234-1234-1234-123456789abc";

  @TempDir Path tempDirectory;

  @Test
  void storesHashesOpensAndDeletesContent() throws Exception {
    LocalObjectStorage storage = new LocalObjectStorage(tempDirectory.toString());

    var stored = storage.put(KEY, stream("hello"), 5);

    assertThat(stored.sizeBytes()).isEqualTo(5);
    assertThat(stored.sha256())
        .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    try (var content = storage.open(KEY)) {
      assertThat(content.readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }
    storage.delete(KEY);
    storage.delete(KEY);
    assertThat(tempDirectory.resolve(KEY)).doesNotExist();
  }

  @Test
  void rejectsInvalidGeneratedKeys() throws Exception {
    LocalObjectStorage storage = new LocalObjectStorage(tempDirectory.toString());

    assertThatThrownBy(() -> storage.put("../escape", stream("x"), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.open(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void removesPartialObjectWhenStreamExceedsLimit() throws Exception {
    LocalObjectStorage storage = new LocalObjectStorage(tempDirectory.toString());

    assertThatThrownBy(() -> storage.put(KEY, stream("too large"), 3))
        .isInstanceOf(DocumentException.class)
        .extracting("errorCode")
        .isEqualTo("DOC-V-003");
    assertThat(tempDirectory.resolve(KEY)).doesNotExist();
  }

  @Test
  void rejectsEmptyAndDuplicateObjects() throws Exception {
    LocalObjectStorage storage = new LocalObjectStorage(tempDirectory.toString());
    assertThatThrownBy(() -> storage.put(KEY, stream(""), 1)).isInstanceOf(DocumentException.class);
    storage.put(KEY, stream("one"), 3);
    assertThatThrownBy(() -> storage.put(KEY, stream("two"), 3)).isInstanceOf(Exception.class);
    assertThat(Files.readString(tempDirectory.resolve(KEY))).isEqualTo("one");
  }

  private static ByteArrayInputStream stream(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }
}
