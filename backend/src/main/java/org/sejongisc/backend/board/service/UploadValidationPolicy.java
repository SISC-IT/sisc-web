package org.sejongisc.backend.board.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

final class UploadValidationPolicy {

  private static final String BINARY_FALLBACK_CONTENT_TYPE = "application/octet-stream";
  private static final int ZIP_ENTRY_TEXT_LIMIT_BYTES = 1024 * 1024;
  private static final int MAX_ZIP_ENTRY_COUNT = 10_000;
  private static final int TEXT_SAMPLE_BYTES = 1024;
  private static final int VIDEO_SIGNATURE_SAMPLE_BYTES = 4096;

  private static final Map<String, String> IMAGE_CONTENT_TYPES_BY_EXTENSION = Map.of(
      "jpg", "image/jpeg",
      "jpeg", "image/jpeg",
      "png", "image/png",
      "webp", "image/webp",
      "gif", "image/gif"
  );

  private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.copyOf(
      IMAGE_CONTENT_TYPES_BY_EXTENSION.values()
  );

  private static final Map<String, Set<String>> ATTACHMENT_CONTENT_TYPES_BY_EXTENSION = Map.of(
      "pdf", Set.of("application/pdf"),
      "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
      "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
      "pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
      "csv", Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain"),
      "txt", Set.of("text/plain"),
      "mp4", Set.of("video/mp4"),
      "webm", Set.of("video/webm"),
      "mov", Set.of("video/quicktime")
  );

  private static final Set<String> REJECTED_ATTACHMENT_EXTENSIONS = Set.of(
      "html", "htm", "svg", "js", "xml", "exe", "sh", "bat", "cmd", "jar",
      "war", "php", "jsp", "docm", "xlsm", "pptm", "zip"
  );

  private static final Set<String> FORBIDDEN_ATTACHMENT_CONTENT_TYPES = Set.of(
      "text/html",
      "image/svg+xml",
      "application/javascript",
      "text/javascript",
      "application/xml",
      "text/xml",
      "application/x-msdownload",
      "application/x-sh",
      "application/x-msdos-program",
      "application/java-archive",
      "application/zip",
      "application/x-zip-compressed",
      "application/x-httpd-php"
  );

  private static final Set<String> BINARY_FALLBACK_EXTENSIONS = Set.of(
      "pdf", "docx", "xlsx", "pptx", "mp4", "webm", "mov"
  );

  private static final Set<String> FORBIDDEN_OOXML_EMBEDDED_EXTENSIONS = Set.of(
      "exe", "sh", "bat", "cmd", "jar", "war", "php", "jsp", "js", "html",
      "htm", "svg", "zip", "gz", "rar", "7z"
  );

  private static final Map<String, String> OOXML_ROOT_PREFIX_BY_EXTENSION = Map.of(
      "docx", "word/",
      "xlsx", "xl/",
      "pptx", "ppt/"
  );

  private static final Map<String, String> OOXML_MAIN_CONTENT_TYPE_BY_EXTENSION = Map.of(
      "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
      "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
      "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"
  );

  private UploadValidationPolicy() {
  }

  static void validateImageType(String extension, String contentType) {
    String expectedContentType = IMAGE_CONTENT_TYPES_BY_EXTENSION.get(extension);
    if (expectedContentType == null
        || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)
        || !expectedContentType.equals(contentType)) {
      throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
  }

  static void validateAttachmentType(String extension, String contentType) {
    if (REJECTED_ATTACHMENT_EXTENSIONS.contains(extension)
        || !ATTACHMENT_CONTENT_TYPES_BY_EXTENSION.containsKey(extension)
        || FORBIDDEN_ATTACHMENT_CONTENT_TYPES.contains(contentType)
        || !isAllowedAttachmentContentType(extension, contentType)) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  private static boolean isAllowedAttachmentContentType(String extension, String contentType) {
    Set<String> allowedContentTypes = ATTACHMENT_CONTENT_TYPES_BY_EXTENSION.get(extension);
    if (allowedContentTypes == null) {
      return false;
    }
    return allowedContentTypes.contains(contentType)
        || (BINARY_FALLBACK_CONTENT_TYPE.equals(contentType)
            && BINARY_FALLBACK_EXTENSIONS.contains(extension));
  }

  static void validateImageSignature(String extension, byte[] bytes) {
    boolean valid = switch (extension) {
      case "jpg", "jpeg" -> hasPrefix(bytes, 0xFF, 0xD8, 0xFF);
      case "png" -> hasPrefix(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
      case "webp" -> bytes.length >= 16
          && hasPrefix(bytes, 0x52, 0x49, 0x46, 0x46)
          && bytes[8] == 'W'
          && bytes[9] == 'E'
          && bytes[10] == 'B'
          && bytes[11] == 'P';
      case "gif" -> hasAsciiPrefix(bytes, "GIF87a") || hasAsciiPrefix(bytes, "GIF89a");
      default -> false;
    };

    if (!valid) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  static void validateAttachmentSignature(String extension, byte[] bytes) {
    switch (extension) {
      case "pdf" -> validatePdf(bytes);
      case "docx", "xlsx", "pptx" -> validateOoxmlPackage(extension, bytes);
      case "csv", "txt" -> validateTextAttachment(bytes);
      case "mp4", "webm", "mov" -> validateVideoAttachment(extension, bytes);
      default -> throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  static boolean isVideoAttachment(String extension) {
    return "mp4".equals(extension) || "webm".equals(extension) || "mov".equals(extension);
  }

  static int videoSignatureSampleBytes() {
    return VIDEO_SIGNATURE_SAMPLE_BYTES;
  }

  static boolean hasAsciiPrefix(byte[] bytes, String prefix) {
    return bytes.length >= prefix.length()
        && new String(bytes, 0, prefix.length(), StandardCharsets.US_ASCII).equals(prefix);
  }

  private static void validatePdf(byte[] bytes) {
    if (!hasPrefix(bytes, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  private static void validateOoxmlPackage(String extension, byte[] bytes) {
    if (!isZipLocalFileHeader(bytes)) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }

    // OOXML 확인
    String expectedRootPrefix = OOXML_ROOT_PREFIX_BY_EXTENSION.get(extension);
    String expectedMainContentType = OOXML_MAIN_CONTENT_TYPE_BY_EXTENSION.get(extension);
    boolean hasContentTypes = false;
    boolean hasExpectedRoot = false;
    boolean hasExpectedMainContentType = false;
    int entryCount = 0;

    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        entryCount++;
        if (entryCount > MAX_ZIP_ENTRY_COUNT) {
          throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }

        String entryName = normalizeZipEntryName(entry.getName());
        if (entryName == null) {
          throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }

        String lowerEntryName = entryName.toLowerCase(Locale.ROOT);
        if (!entry.isDirectory() && lowerEntryName.startsWith(expectedRootPrefix)) {
          hasExpectedRoot = true;
        }
        // 위험 항목 차단
        if (lowerEntryName.endsWith("/vbaproject.bin")
            || lowerEntryName.equals("vbaproject.bin")
            || lowerEntryName.contains("activex/")
            || lowerEntryName.contains("embeddings/oleobject")
            || lowerEntryName.endsWith(".bin")
            || isForbiddenOoxmlEmbeddedName(lowerEntryName)) {
          throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }
        if ("[content_types].xml".equals(lowerEntryName)) {
          hasContentTypes = true;
          String contentTypesXml = readZipEntryText(zip).toLowerCase(Locale.ROOT);
          if (contentTypesXml.contains("macroenabled")
              || contentTypesXml.contains("vbaproject")
              || contentTypesXml.contains("application/vnd.ms-office.vbaproject")) {
            throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
          }
          hasExpectedMainContentType = contentTypesXml.contains(expectedMainContentType);
        }
      }
    } catch (CustomException e) {
      throw e;
    } catch (IOException e) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }

    if (!hasContentTypes || !hasExpectedRoot || !hasExpectedMainContentType) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  private static void validateTextAttachment(byte[] bytes) {
    if (hasForbiddenBinarySignature(bytes) || hasBrowserInterpretedTextPrefix(bytes)) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }

    // 제어문자 차단
    for (byte value : bytes) {
      int unsigned = value & 0xFF;
      boolean allowedControl = unsigned == 0x09 || unsigned == 0x0A || unsigned == 0x0D;
      if (unsigned == 0 || (unsigned < 0x20 && !allowedControl)) {
        throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
      }
    }
  }

  private static void validateVideoAttachment(String extension, byte[] bytes) {
    boolean valid = switch (extension) {
      case "mp4", "mov" -> hasIsoBaseMediaFileSignature(bytes);
      case "webm" -> hasPrefix(bytes, 0x1A, 0x45, 0xDF, 0xA3)
          && containsAsciiSample(bytes, "webm");
      default -> false;
    };

    if (!valid) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  private static String normalizeZipEntryName(String entryName) {
    if (!StringUtils.hasText(entryName)) {
      return null;
    }
    String normalized = entryName.replace('\\', '/');
    if (normalized.startsWith("/")
        || normalized.contains("../")
        || normalized.startsWith("../")
        || normalized.contains("..\\")) {
      return null;
    }
    return normalized;
  }

  private static boolean isForbiddenOoxmlEmbeddedName(String lowerEntryName) {
    String extension = StringUtils.getFilenameExtension(lowerEntryName);
    return StringUtils.hasText(extension) && FORBIDDEN_OOXML_EMBEDDED_EXTENSIONS.contains(extension);
  }

  private static String readZipEntryText(ZipInputStream zip) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int totalBytes = 0;
    int read;
    while ((read = zip.read(buffer)) != -1) {
      totalBytes += read;
      if (totalBytes > ZIP_ENTRY_TEXT_LIMIT_BYTES) {
        throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
      }
      output.write(buffer, 0, read);
    }
    return output.toString(StandardCharsets.UTF_8);
  }

  private static boolean hasForbiddenBinarySignature(byte[] bytes) {
    return hasPrefix(bytes, 0x4D, 0x5A)
        || hasPrefix(bytes, 0x7F, 0x45, 0x4C, 0x46)
        || hasPrefix(bytes, 0xCA, 0xFE, 0xBA, 0xBE)
        || hasPrefix(bytes, 0x1F, 0x8B)
        || hasPrefix(bytes, 0x52, 0x61, 0x72, 0x21)
        || hasPrefix(bytes, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)
        || isZipLocalFileHeader(bytes)
        || hasAsciiPrefix(bytes, "#!");
  }

  private static boolean hasBrowserInterpretedTextPrefix(byte[] bytes) {
    String sample = new String(bytes, 0, Math.min(bytes.length, TEXT_SAMPLE_BYTES), StandardCharsets.UTF_8)
        .stripLeading()
        .toLowerCase(Locale.ROOT);
    return sample.startsWith("<!doctype html")
        || sample.startsWith("<html")
        || sample.startsWith("<script")
        || sample.startsWith("<svg")
        || sample.startsWith("<?xml");
  }

  private static boolean isZipLocalFileHeader(byte[] bytes) {
    return hasPrefix(bytes, 0x50, 0x4B, 0x03, 0x04);
  }

  private static boolean hasIsoBaseMediaFileSignature(byte[] bytes) {
    return bytes.length >= 12 && hasAsciiAt(bytes, 4, "ftyp");
  }

  private static boolean containsAsciiSample(byte[] bytes, String text) {
    String sample = new String(
        bytes,
        0,
        Math.min(bytes.length, VIDEO_SIGNATURE_SAMPLE_BYTES),
        StandardCharsets.US_ASCII
    ).toLowerCase(Locale.ROOT);
    return sample.contains(text.toLowerCase(Locale.ROOT));
  }

  private static boolean hasAsciiAt(byte[] bytes, int offset, String value) {
    if (bytes.length < offset + value.length()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (bytes[offset + i] != value.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasPrefix(byte[] bytes, int... expected) {
    if (bytes.length < expected.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      if ((bytes[i] & 0xFF) != expected[i]) {
        return false;
      }
    }
    return true;
  }
}
