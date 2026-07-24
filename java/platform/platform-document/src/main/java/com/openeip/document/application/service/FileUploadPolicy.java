package com.openeip.document.application.service;

import com.openeip.document.shared.exception.DocumentException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Validates untrusted upload metadata before opening the content stream. */
@Component
public class FileUploadPolicy {

  private static final Map<String, String> SUFFIX_TYPES =
      Map.ofEntries(
          Map.entry("txt", "text/plain"),
          Map.entry("pdf", "application/pdf"),
          Map.entry(
              "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
          Map.entry(
              "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
          Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
          Map.entry("png", "image/png"),
          Map.entry("jpg", "image/jpeg"),
          Map.entry("jpeg", "image/jpeg"));

  public UploadDescriptor validate(
      String originalFilename, String declaredContentType, long declaredSize, long maxBytes) {
    String filename = validateFilename(originalFilename);
    if (declaredSize <= 0) {
      throw DocumentException.invalid("File must not be empty");
    }
    if (declaredSize > maxBytes) {
      throw DocumentException.tooLarge(maxBytes);
    }

    String suffix = StringUtils.getFilenameExtension(filename);
    if (!StringUtils.hasText(suffix)) {
      throw DocumentException.unsupported();
    }
    String expectedType = SUFFIX_TYPES.get(suffix.toLowerCase(Locale.ROOT));
    String contentType = normalizeContentType(declaredContentType);
    if (expectedType == null || !expectedType.equals(contentType)) {
      throw DocumentException.unsupported();
    }
    return new UploadDescriptor(filename, contentType);
  }

  private static String validateFilename(String originalFilename) {
    if (!StringUtils.hasText(originalFilename)
        || originalFilename.length() > 255
        || originalFilename.contains("/")
        || originalFilename.contains("\\")
        || originalFilename.codePoints().anyMatch(Character::isISOControl)) {
      throw DocumentException.invalid("Invalid filename");
    }
    String filename = originalFilename.trim();
    if (filename.isEmpty() || filename.equals(".") || filename.equals("..")) {
      throw DocumentException.invalid("Invalid filename");
    }
    return filename;
  }

  private static String normalizeContentType(String declaredContentType) {
    if (!StringUtils.hasText(declaredContentType)) {
      throw DocumentException.unsupported();
    }
    try {
      MediaType parsed = MediaType.parseMediaType(declaredContentType);
      return parsed.getType().toLowerCase(Locale.ROOT)
          + "/"
          + parsed.getSubtype().toLowerCase(Locale.ROOT);
    } catch (InvalidMediaTypeException exception) {
      throw DocumentException.unsupported();
    }
  }

  public record UploadDescriptor(String originalName, String contentType) {}
}
