package org.sejongisc.backend.board.service;

import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.config.UploadProperties;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileUploadService {

  private final UploadProperties uploadProperties;
  private Path rootLocation;

  @PostConstruct
  public void init() {
    if (!StringUtils.hasText(uploadProperties.getRootLocation())) {
      throw new IllegalStateException("app.upload.root-location 설정이 필요합니다.");
    }
    try {
      this.rootLocation = Paths.get(uploadProperties.getRootLocation()).toAbsolutePath().normalize();
      Files.createDirectories(this.rootLocation);
    } catch (IOException e) {
      throw new RuntimeException("업로드할 디렉토리를 생성할 수 없습니다.", e);
    }
  }

  /**
   * 기존 일반 게시글 첨부 저장 경로
   * savedFilename 반환 계약 유지, 리치 에디터 첨부 검증 경로로 위임
   */
  public String store(MultipartFile file) {
    return storeFile(file).savedFilename();
  }

  public StoredFile storeImage(MultipartFile file) {
    ValidatedUpload upload = validateImage(file);
    ImageDimension dimension = decodeImage(upload.bytes(), upload.extension());
    return storeInDirectory(upload, monthlyDirectory("images"))
        .withDimension(dimension.width(), dimension.height());
  }

  public StoredFile storeFile(MultipartFile file) {
    ValidatedUpload upload = validateAttachment(file);
    return storeInDirectory(upload, monthlyDirectory("files"));
  }

  /**
   * 파일 삭제
   * @param filename 삭제할 파일명
   */
  public void delete(String filename) {
    try {
      Path file = this.rootLocation.resolve(filename).normalize();
      if (!isInsideRoot(file)) {
        throw new RuntimeException("현재 디렉토리 밖의 파일은 삭제할 수 없습니다.");
      }
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new RuntimeException("파일 삭제 실패: " + filename, e);
    }
  }

  /**
   * 저장된 루트 경로 반환
   * @return Path
   */
  public Path getRootLocation() {
    return this.rootLocation;
  }

  public String buildPublicUrl(String publicPath) {
    String publicBaseUrl = uploadProperties.getPublicBaseUrl();
    if (!StringUtils.hasText(publicBaseUrl)) {
      return publicPath;
    }
    String baseUrl = publicBaseUrl.endsWith("/")
        ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
        : publicBaseUrl;
    String normalizedPublicPath = publicPath.startsWith("/") ? publicPath : "/" + publicPath;
    return baseUrl + normalizedPublicPath;
  }

  public String buildPublicPath(String savedFilename) {
    return normalizePublicPathPrefix() + "/" + savedFilename;
  }

  private ValidatedUpload validateImage(MultipartFile file) {
    validateNotEmpty(file);
    String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
    String extension = extractExtension(originalFilename);
    String contentType = normalizeContentType(file.getContentType());

    UploadValidationPolicy.validateImageType(extension, contentType);

    byte[] bytes = readValidatedBytes(file, uploadProperties.getImageMaxSize().toBytes());
    UploadValidationPolicy.validateImageSignature(extension, bytes);
    return ValidatedUpload.fromBytes(originalFilename, extension, contentType, bytes);
  }

  private ValidatedUpload validateAttachment(MultipartFile file) {
    validateNotEmpty(file);
    String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
    String extension = extractExtension(originalFilename);
    String contentType = normalizeContentType(file.getContentType());

    UploadValidationPolicy.validateAttachmentType(extension, contentType);

    long maxBytes = attachmentMaxSizeBytes(extension);
    if (UploadValidationPolicy.isVideoAttachment(extension)) {
      validateReportedSize(file, maxBytes);
      byte[] sample = readHeadBytes(file, UploadValidationPolicy.videoSignatureSampleBytes());
      UploadValidationPolicy.validateAttachmentSignature(extension, sample);
      return ValidatedUpload.fromSource(originalFilename, extension, contentType, file, maxBytes);
    }

    byte[] bytes = readValidatedBytes(file, maxBytes);
    UploadValidationPolicy.validateAttachmentSignature(extension, bytes);
    return ValidatedUpload.fromBytes(originalFilename, extension, contentType, bytes);
  }

  private long attachmentMaxSizeBytes(String extension) {
    return UploadValidationPolicy.isVideoAttachment(extension)
        ? uploadProperties.getVideoMaxSize().toBytes()
        : uploadProperties.getAttachmentMaxSize().toBytes();
  }

  private ImageDimension decodeImage(byte[] bytes, String extension) {
    try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (imageInput == null) {
        throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
      }

      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
      if (!readers.hasNext()) {
        if ("webp".equals(extension)) {
          // WebP 확인
          return decodeWebpDimension(bytes);
        }
        throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
      }

      ImageReader reader = readers.next();
      try {
        reader.setInput(imageInput, true, true);
        BufferedImage image = reader.read(0);
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
          throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }
        return new ImageDimension(image.getWidth(), image.getHeight());
      } finally {
        reader.dispose();
      }
    } catch (CustomException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      if ("webp".equals(extension)) {
        return decodeWebpDimension(bytes);
      }
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  private ImageDimension decodeWebpDimension(byte[] bytes) {
    if (bytes.length < 30
        || !UploadValidationPolicy.hasAsciiPrefix(bytes, "RIFF")
        || bytes[8] != 'W'
        || bytes[9] != 'E'
        || bytes[10] != 'B'
        || bytes[11] != 'P') {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }

    String chunkType = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
    // 청크 분기
    return switch (chunkType) {
      case "VP8X" -> new ImageDimension(
          readLittleEndian24(bytes, 24) + 1,
          readLittleEndian24(bytes, 27) + 1
      );
      case "VP8 " -> decodeLossyWebpDimension(bytes);
      case "VP8L" -> decodeLosslessWebpDimension(bytes);
      default -> throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    };
  }

  private ImageDimension decodeLossyWebpDimension(byte[] bytes) {
    if (bytes.length < 30
        || bytes[23] != (byte) 0x9D
        || bytes[24] != 0x01
        || bytes[25] != 0x2A) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
    int width = ((bytes[27] & 0x3F) << 8) | (bytes[26] & 0xFF);
    int height = ((bytes[29] & 0x3F) << 8) | (bytes[28] & 0xFF);
    return new ImageDimension(width, height);
  }

  private ImageDimension decodeLosslessWebpDimension(byte[] bytes) {
    if (bytes.length < 25 || bytes[20] != 0x2F) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
    int b1 = bytes[21] & 0xFF;
    int b2 = bytes[22] & 0xFF;
    int b3 = bytes[23] & 0xFF;
    int b4 = bytes[24] & 0xFF;
    int width = 1 + (((b2 & 0x3F) << 8) | b1);
    int height = 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
    return new ImageDimension(width, height);
  }

  private int readLittleEndian24(byte[] bytes, int offset) {
    if (bytes.length < offset + 3) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
    return (bytes[offset] & 0xFF)
        | ((bytes[offset + 1] & 0xFF) << 8)
        | ((bytes[offset + 2] & 0xFF) << 16);
  }

  private StoredFile storeInDirectory(ValidatedUpload upload, String directory) {
    String savedFilename = directory + "/" + UUID.randomUUID() + "." + upload.extension();
    Path destinationFile = this.rootLocation.resolve(savedFilename).normalize();

    // 루트 이탈 차단
    if (!isInsideRoot(destinationFile)) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }

    try {
      Files.createDirectories(destinationFile.getParent());
      if (upload.bytes() != null) {
        Files.write(destinationFile, upload.bytes());
      } else {
        copyValidated(upload.source(), destinationFile, upload.maxBytes());
      }
    } catch (IOException e) {
      throw new RuntimeException("파일 저장 실패: " + upload.originalFilename(), e);
    }
    long fileSize = resolveStoredFileSize(destinationFile, upload);

    String publicPath = normalizePublicPathPrefix() + "/" + savedFilename;
    return new StoredFile(
        savedFilename,
        upload.originalFilename(),
        destinationFile.toString(),
        publicPath,
        upload.contentType(),
        fileSize,
        null,
        null
    );
  }

  private String monthlyDirectory(String type) {
    YearMonth now = YearMonth.now();
    return type + "/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue());
  }

  private void validateNotEmpty(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  private void validateReportedSize(MultipartFile file, long maxBytes) {
    if (file.getSize() <= 0 || file.getSize() > maxBytes) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }
  }

  private byte[] readValidatedBytes(MultipartFile file, long maxBytes) {
    validateReportedSize(file, maxBytes);

    try {
      byte[] bytes = file.getBytes();
      if (bytes.length == 0 || bytes.length > maxBytes) {
        throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
      }
      return bytes;
    } catch (CustomException e) {
      throw e;
    } catch (IOException e) {
      throw new RuntimeException("파일 읽기 실패", e);
    }
  }

  private byte[] readHeadBytes(MultipartFile file, int maxBytes) {
    try (InputStream input = file.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream(maxBytes)) {
      byte[] buffer = new byte[Math.min(maxBytes, 4096)];
      int remaining = maxBytes;
      int read;
      while (remaining > 0
          && (read = input.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
        output.write(buffer, 0, read);
        remaining -= read;
      }
      byte[] bytes = output.toByteArray();
      if (bytes.length == 0) {
        throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
      }
      return bytes;
    } catch (CustomException e) {
      throw e;
    } catch (IOException e) {
      throw new RuntimeException("파일 읽기 실패", e);
    }
  }

  private long copyValidated(MultipartFile file, Path destinationFile, long maxBytes) throws IOException {
    try (InputStream input = file.getInputStream();
        OutputStream output = Files.newOutputStream(destinationFile)) {
      byte[] buffer = new byte[8192];
      long totalBytes = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        totalBytes += read;
        if (totalBytes > maxBytes) {
          throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }
        output.write(buffer, 0, read);
      }
      if (totalBytes == 0) {
        throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
      }
      return totalBytes;
    } catch (CustomException | IOException e) {
      try {
        Files.deleteIfExists(destinationFile);
      } catch (IOException deleteException) {
        e.addSuppressed(deleteException);
      }
      throw e;
    }
  }

  private long resolveStoredFileSize(Path destinationFile, ValidatedUpload upload) {
    if (upload.bytes() != null) {
      return upload.bytes().length;
    }
    try {
      return Files.size(destinationFile);
    } catch (IOException e) {
      throw new RuntimeException("파일 크기 확인 실패: " + upload.originalFilename(), e);
    }
  }

  private String extractExtension(String filename) {
    String extension = StringUtils.getFilenameExtension(filename);
    if (!StringUtils.hasText(extension)) {
      return "";
    }
    String normalized = extension.toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9]{1,10}")) {
      return "";
    }
    return normalized;
  }

  private String normalizeContentType(String contentType) {
    if (!StringUtils.hasText(contentType)) {
      return "application/octet-stream";
    }
    return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
  }

  private String cleanOriginalFilename(String originalFilename) {
    String cleaned = StringUtils.cleanPath(originalFilename == null ? "upload" : originalFilename);
    int slashIndex = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
    String filename = slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned;
    return StringUtils.hasText(filename) ? filename : "upload";
  }

  private boolean isInsideRoot(Path path) {
    return path.normalize().startsWith(this.rootLocation);
  }

  private String normalizePublicPathPrefix() {
    String publicPathPrefix = uploadProperties.getPublicPathPrefix();
    if (!StringUtils.hasText(publicPathPrefix)) {
      return "/uploads";
    }
    return publicPathPrefix.startsWith("/") ? publicPathPrefix : "/" + publicPathPrefix;
  }

  private record ValidatedUpload(
      String originalFilename,
      String extension,
      String contentType,
      byte[] bytes,
      MultipartFile source,
      long maxBytes
  ) {
    static ValidatedUpload fromBytes(
        String originalFilename,
        String extension,
        String contentType,
        byte[] bytes
    ) {
      return new ValidatedUpload(originalFilename, extension, contentType, bytes, null, bytes.length);
    }

    static ValidatedUpload fromSource(
        String originalFilename,
        String extension,
        String contentType,
        MultipartFile source,
        long maxBytes
    ) {
      return new ValidatedUpload(originalFilename, extension, contentType, null, source, maxBytes);
    }
  }

  private record ImageDimension(Integer width, Integer height) {
  }

  public record StoredFile(
      String savedFilename,
      String originalFilename,
      String filePath,
      String publicPath,
      String contentType,
      Long fileSize,
      Integer width,
      Integer height
  ) {
    StoredFile withDimension(Integer width, Integer height) {
      return new StoredFile(
          savedFilename,
          originalFilename,
          filePath,
          publicPath,
          contentType,
          fileSize,
          width,
          height
      );
    }
  }
}
