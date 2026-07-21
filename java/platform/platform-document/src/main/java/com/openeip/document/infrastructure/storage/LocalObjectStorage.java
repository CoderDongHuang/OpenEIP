package com.openeip.document.infrastructure.storage;

import com.openeip.document.domain.storage.ObjectStorage;
import com.openeip.document.shared.exception.DocumentException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Single-node filesystem adapter with generated-key and symlink containment checks. */
@Component
public class LocalObjectStorage implements ObjectStorage {

  private static final Pattern OBJECT_KEY = Pattern.compile("^[0-9a-f]{2}/[0-9a-f-]{36}$");
  private static final int BUFFER_SIZE = 16 * 1024;
  private final Path root;

  @SuppressFBWarnings(
      value = {"PATH_TRAVERSAL_IN", "CT_CONSTRUCTOR_THROW"},
      justification =
          "The trusted root is validated and unsafe storage configuration must fail fast.")
  public LocalObjectStorage(@Value("${openeip.document.storage-root:./data/files}") String root)
      throws IOException {
    Path configured = Path.of(root).toAbsolutePath().normalize();
    Files.createDirectories(configured);
    if (Files.isSymbolicLink(configured)
        || !Files.isDirectory(configured, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Document storage root must be a real directory");
    }
    this.root = configured.toRealPath(LinkOption.NOFOLLOW_LINKS);
  }

  @Override
  public StoredObject put(String objectKey, InputStream content, long maxBytes) throws IOException {
    Path target = resolve(objectKey);
    Path parent = Objects.requireNonNull(target.getParent(), "Generated object must have a prefix");
    Files.createDirectories(parent);
    if (Files.isSymbolicLink(parent)) {
      throw new IOException("Object prefix must not be a symbolic link");
    }

    MessageDigest digest = sha256();
    long total = 0;
    boolean created = false;
    try {
      try (OutputStream output =
          Files.newOutputStream(
              target,
              new OpenOption[] {
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS
              })) {
        created = true;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = content.read(buffer)) != -1) {
          total += read;
          if (total > maxBytes) {
            throw DocumentException.tooLarge(maxBytes);
          }
          output.write(buffer, 0, read);
          digest.update(buffer, 0, read);
        }
      }
    } catch (IOException | RuntimeException exception) {
      if (created) {
        Files.deleteIfExists(target);
      }
      throw exception;
    }
    if (total == 0) {
      Files.deleteIfExists(target);
      throw DocumentException.invalid("File must not be empty");
    }
    return new StoredObject(total, HexFormat.of().formatHex(digest.digest()));
  }

  @Override
  public InputStream open(String objectKey) throws IOException {
    Path target = resolve(objectKey);
    if (Files.isSymbolicLink(target)) {
      throw new IOException("Object must not be a symbolic link");
    }
    return Files.newInputStream(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
  }

  @Override
  public void delete(String objectKey) throws IOException {
    Path target = resolve(objectKey);
    if (Files.isSymbolicLink(target)) {
      throw new IOException("Object must not be a symbolic link");
    }
    Files.deleteIfExists(target);
  }

  private Path resolve(String objectKey) {
    if (objectKey == null || !OBJECT_KEY.matcher(objectKey).matches()) {
      throw new IllegalArgumentException("Invalid generated object key");
    }
    Path target = root.resolve(objectKey).normalize();
    if (!target.startsWith(root)) {
      throw new IllegalArgumentException("Object key escapes storage root");
    }
    return target;
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
