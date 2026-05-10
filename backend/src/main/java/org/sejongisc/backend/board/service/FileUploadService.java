package org.sejongisc.backend.board.service;


import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileUploadService {

  private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
      "image/jpeg",
      "image/png",
      "image/webp",
      "image/gif"
  );
  private static final Map<String, String> IMAGE_EXTENSIONS_BY_CONTENT_TYPE = Map.of(
      "image/jpeg", "jpg",
      "image/png", "png",
      "image/webp", "webp",
      "image/gif", "gif"
  );

  private Path rootLocation;

  @Value("${app.upload.root-location:${user.dir}/uploads}")
  private String uploadRootLocation;

  @Value("${app.upload.public-path-prefix:/uploads}")
  private String publicPathPrefix;

  @Value("${app.upload.public-base-url:}")
  private String publicBaseUrl;

  // 서비스 생성 시 업로드 디렉토리가 없으면 생성
  @PostConstruct
  public void init() {
    try {
      this.rootLocation = Paths.get(uploadRootLocation).toAbsolutePath().normalize();
      Files.createDirectories(this.rootLocation);
    } catch (IOException e) {
      throw new RuntimeException("업로드할 디렉토리를 생성할 수 없습니다.", e);
    }
  }

  /**
   * 파일 저장
   * @param file 업로드된 파일
   * @return 저장된 파일명 (UUID 포함)
   */
  public String store(MultipartFile file) {
    if (file.isEmpty()) {
      throw new RuntimeException("빈 파일은 저장할 수 없습니다.");
    }

    // 원본 파일명 정리 (경로 조작 방지)
    String originalFilename = cleanOriginalFilename(file.getOriginalFilename());

    try {
      // 파일명 중복 방지를 위해 UUID 추가
      String savedFilename = UUID.randomUUID().toString() + "_" + originalFilename;

      // 저장할 경로 생성
      Path destinationFile = this.rootLocation.resolve(savedFilename).normalize();

      // 상위 디렉토리로 벗어나려는지 보안 체크
      if (!isInsideRoot(destinationFile)) {
        throw new RuntimeException("현재 디렉토리 밖에 저장할 수 없습니다.");
      }

      // 파일 복사 (이미 존재하면 덮어쓰기)
      try (InputStream inputStream = file.getInputStream()) {
        Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
      }

      return savedFilename; // 데이터베이스에 저장할 파일명 리턴

    } catch (IOException e) {
      throw new RuntimeException("파일 저장 실패: " + originalFilename, e);
    }
  }

  public StoredFile storeImage(MultipartFile file) {
    validateNotEmpty(file);
    String contentType = normalizeContentType(file.getContentType());
    if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
      throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    String extension = IMAGE_EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
    StoredFile storedFile = storeInDirectory(file, monthlyDirectory("images"), extension);
    ImageDimension dimension = readImageDimension(storedFile.savedFilename());
    return storedFile.withDimension(dimension.width(), dimension.height());
  }

  public StoredFile storeFile(MultipartFile file) {
    validateNotEmpty(file);
    String extension = StringUtils.getFilenameExtension(cleanOriginalFilename(file.getOriginalFilename()));
    return storeInDirectory(file, monthlyDirectory("files"), normalizeExtension(extension));
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
    if (!StringUtils.hasText(publicBaseUrl)) {
      return publicPath;
    }
    String baseUrl = publicBaseUrl.endsWith("/")
        ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
        : publicBaseUrl;
    String normalizedPublicPath = publicPath.startsWith("/") ? publicPath : "/" + publicPath;
    return baseUrl + normalizedPublicPath;
  }

  // -------------- private helper 메서드 --------------

  private StoredFile storeInDirectory(MultipartFile file, String directory, String extension) {
    String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
    String safeExtension = StringUtils.hasText(extension) ? "." + extension : "";
    String savedFilename = directory + "/" + UUID.randomUUID() + safeExtension;
    Path destinationFile = this.rootLocation.resolve(savedFilename).normalize();

    if (!isInsideRoot(destinationFile)) {
      throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
    }

    try {
      Files.createDirectories(destinationFile.getParent());
      try (InputStream inputStream = file.getInputStream()) {
        Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new RuntimeException("파일 저장 실패: " + originalFilename, e);
    }

    String publicPath = normalizePublicPathPrefix() + "/" + savedFilename;
    return new StoredFile(
        savedFilename,
        originalFilename,
        destinationFile.toString(),
        publicPath,
        normalizeContentType(file.getContentType()),
        file.getSize(),
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

  private String normalizeContentType(String contentType) {
    return StringUtils.hasText(contentType)
        ? contentType.toLowerCase(Locale.ROOT)
        : "application/octet-stream";
  }

  private String cleanOriginalFilename(String originalFilename) {
    String cleaned = StringUtils.cleanPath(originalFilename == null ? "upload" : originalFilename);
    return StringUtils.hasText(cleaned) ? cleaned : "upload";
  }

  private String normalizeExtension(String extension) {
    if (!StringUtils.hasText(extension)) {
      return "";
    }
    String normalized = extension.toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9]{1,10}")) {
      return "";
    }
    return normalized;
  }

  private ImageDimension readImageDimension(String savedFilename) {
    Path file = this.rootLocation.resolve(savedFilename).normalize();
    if (!isInsideRoot(file)) {
      return new ImageDimension(null, null);
    }
    try {
      BufferedImage image = ImageIO.read(file.toFile());
      if (image == null) {
        return new ImageDimension(null, null);
      }
      return new ImageDimension(image.getWidth(), image.getHeight());
    } catch (IOException e) {
      return new ImageDimension(null, null);
    }
  }

  private boolean isInsideRoot(Path path) {
    return path.normalize().startsWith(this.rootLocation);
  }

  private String normalizePublicPathPrefix() {
    if (!StringUtils.hasText(publicPathPrefix)) {
      return "/uploads";
    }
    return publicPathPrefix.startsWith("/") ? publicPathPrefix : "/" + publicPathPrefix;
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
