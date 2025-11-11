package org.sejongisc.backend.board.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

  @TempDir
  Path tempDir;

  FileUploadService fileUploadService;

  @BeforeEach
  void setUp() {
    fileUploadService = new FileUploadService();

    ReflectionTestUtils.setField(fileUploadService, "rootLocation", tempDir);
  }

  @Test
  @DisplayName("파일 저장 성공")
  void store_success() throws IOException {
    String originalFilename = "test-file.txt";
    MockMultipartFile file = new MockMultipartFile(
        "file",
        originalFilename,
        "text/plain",
        "Hello, World!".getBytes()
    );

    String savedFilename = fileUploadService.store(file);

    assertThat(savedFilename).endsWith("_" + originalFilename);
    assertThat(savedFilename.length()).isGreaterThan(originalFilename.length() + 36);

    Path expectedFilePath = tempDir.resolve(savedFilename);
    assertThat(Files.exists(expectedFilePath)).isTrue();
    assertThat(Files.readAllBytes(expectedFilePath)).isEqualTo("Hello, World!".getBytes());
  }

  @Test
  @DisplayName("빈 파일 저장 시 예외 발생")
  void store_emptyFile_throwException() {
    MockMultipartFile emptyFile = new MockMultipartFile(
        "file",
        "empty.txt",
        "text/plain",
        new byte[0]
    );

    assertThatThrownBy(() -> fileUploadService.store(emptyFile))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("빈 파일은 저장할 수 없습니다.");
  }

  @Test
  @DisplayName("파일 삭제 성공")
  void delete_success() throws IOException {
    MockMultipartFile file = new MockMultipartFile("file", "delete-me.txt", "text/plain", "data".getBytes());
    String savedFilename = fileUploadService.store(file);
    Path filePath = tempDir.resolve(savedFilename);

    assertThat(Files.exists(filePath)).isTrue();

    fileUploadService.delete(savedFilename);

    assertThat(Files.exists(filePath)).isFalse();
  }

  @Test
  @DisplayName("존재하지 않는 파일 삭제 시 예외 발생 안 함")
  void delete_nonExistingFile_noException() {
    String nonExistingFilename = "non-existing-file.txt";
    Path filePath = tempDir.resolve(nonExistingFilename);
    assertThat(Files.exists(filePath)).isFalse();

    // FileUploadService.java의 delete는 예외를 throw하지 않으므로,
    // 예외가 발생하지 않는지 확인합니다.
    assertDoesNotThrow(() ->
        fileUploadService.delete(nonExistingFilename)
    );
  }
}