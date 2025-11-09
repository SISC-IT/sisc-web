package org.sejongisc.backend.board.service;


import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileUploadService {

//  @Value("${file.upload-dir}")
  private final static String UPLOAD_DIRS = "C:/uploads/";

  private Path rootLocation;

  // 서비스 생성 시 업로드 디렉토리가 없으면 생성
  @PostConstruct
  public void init() {
    try {
      this.rootLocation = Paths.get(UPLOAD_DIRS).toAbsolutePath().normalize();
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
    String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

    try {
      // 파일명 중복 방지를 위해 UUID 추가
      String savedFilename = UUID.randomUUID().toString() + "_" + originalFilename;

      // 저장할 경로 생성
      Path destinationFile = this.rootLocation.resolve(savedFilename).normalize();

      // 상위 디렉토리로 벗어나려는지 보안 체크
      if (!destinationFile.getParent().equals(this.rootLocation)) {
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

  /**
   * 파일 삭제
   * @param filename 삭제할 파일명
   */
  public void delete(String filename) {
    try {
      Path file = this.rootLocation.resolve(filename).normalize();
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
}
