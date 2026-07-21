package org.sejongisc.backend.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.config.UploadProperties;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

  @TempDir
  Path tempDir;

  FileUploadService fileUploadService;
  UploadProperties uploadProperties;

  @BeforeEach
  void setUp() {
    uploadProperties = new UploadProperties();
    uploadProperties.setRootLocation(tempDir.toString());
    uploadProperties.setPublicPathPrefix("/uploads");
    uploadProperties.setPublicBaseUrl("");
    uploadProperties.setImageMaxSize(DataSize.ofMegabytes(10));
    uploadProperties.setAttachmentMaxSize(DataSize.ofMegabytes(30));
    uploadProperties.setVideoMaxSize(DataSize.ofMegabytes(100));
    uploadProperties.setAdminExcelMaxSize(DataSize.ofMegabytes(5));
    fileUploadService = new FileUploadService(uploadProperties);
    fileUploadService.init();
  }

  @Test
  @DisplayName("기존 store(): 강화된 첨부파일 저장 검증 경유")
  void store_success() throws IOException {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "test-file.txt",
        "text/plain",
        "Hello, World!".getBytes(StandardCharsets.UTF_8)
    );

    String savedFilename = fileUploadService.store(file);

    assertThat(savedFilename).matches(monthlyPattern("files", "txt"));
    assertThat(savedFilename).doesNotContain("test-file");
    Path expectedFilePath = tempDir.resolve(savedFilename);
    assertThat(Files.exists(expectedFilePath)).isTrue();
    assertThat(Files.readString(expectedFilePath)).isEqualTo("Hello, World!");
  }

  @Test
  @DisplayName("빈 업로드 파일 거절")
  void store_emptyFile_throwException() {
    MockMultipartFile emptyFile = new MockMultipartFile(
        "file",
        "empty.txt",
        "text/plain",
        new byte[0]
    );

    assertCustomException(
        () -> fileUploadService.store(emptyFile),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("브라우저 해석 가능 첨부 확장자 거절")
  void storeFile_htmlExtension_throwException() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "index.html",
        "text/html",
        "<html></html>".getBytes(StandardCharsets.UTF_8)
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("PDF 첨부파일: 확장자, MIME, 시그니처 일치 시 저장")
  void storeFile_pdf_success() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "report.pdf",
        "application/pdf",
        "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII)
    );

    FileUploadService.StoredFile storedFile = fileUploadService.storeFile(file);

    assertThat(storedFile.savedFilename()).matches(monthlyPattern("files", "pdf"));
    assertThat(storedFile.publicPath()).isEqualTo("/uploads/" + storedFile.savedFilename());
    assertThat(storedFile.contentType()).isEqualTo("application/pdf");
  }

  @Test
  @DisplayName("PDF 첨부파일 octet-stream MIME 저장")
  void storeFile_pdfOctetStream_success() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "report.pdf",
        "application/octet-stream",
        "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII)
    );

    FileUploadService.StoredFile storedFile = fileUploadService.storeFile(file);

    assertThat(storedFile.savedFilename()).matches(monthlyPattern("files", "pdf"));
    assertThat(storedFile.contentType()).isEqualTo("application/octet-stream");
  }

  @Test
  @DisplayName("텍스트 첨부파일 octet-stream MIME 거절")
  void storeFile_textOctetStream_throwException() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "notes.txt",
        "application/octet-stream",
        "plain text".getBytes(StandardCharsets.UTF_8)
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("MP4 영상 첨부파일 저장")
  void storeFile_mp4Video_success() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "seminar.mp4",
        "video/mp4",
        mp4Bytes()
    );

    FileUploadService.StoredFile storedFile = fileUploadService.storeFile(file);

    assertThat(storedFile.savedFilename()).matches(monthlyPattern("files", "mp4"));
    assertThat(storedFile.contentType()).isEqualTo("video/mp4");
    assertThat(Files.exists(tempDir.resolve(storedFile.savedFilename()))).isTrue();
  }

  @Test
  @DisplayName("WebM 영상 첨부파일 저장")
  void storeFile_webmVideo_success() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "meeting.webm",
        "video/webm",
        webmBytes()
    );

    FileUploadService.StoredFile storedFile = fileUploadService.storeFile(file);

    assertThat(storedFile.savedFilename()).matches(monthlyPattern("files", "webm"));
    assertThat(storedFile.contentType()).isEqualTo("video/webm");
  }

  @Test
  @DisplayName("MOV 영상 첨부파일 저장")
  void storeFile_movVideo_success() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "iphone.mov",
        "video/quicktime",
        movBytes()
    );

    FileUploadService.StoredFile storedFile = fileUploadService.storeFile(file);

    assertThat(storedFile.savedFilename()).matches(monthlyPattern("files", "mov"));
    assertThat(storedFile.contentType()).isEqualTo("video/quicktime");
  }

  @Test
  @DisplayName("영상 MIME/확장자 불일치 거절")
  void storeFile_videoMimeExtensionMismatch_throwException() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "seminar.mp4",
        "video/webm",
        mp4Bytes()
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("영상 시그니처 불일치 거절")
  void storeFile_videoSignatureMismatch_throwException() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "seminar.mp4",
        "video/mp4",
        "not a video".getBytes(StandardCharsets.UTF_8)
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("영상 용량 제한 초과 거절")
  void storeFile_oversizedVideo_throwException() {
    MultipartFile file = new SizedMultipartFile(
        "file",
        "seminar.mp4",
        "video/mp4",
        uploadProperties.getVideoMaxSize().toBytes() + 1,
        mp4Bytes()
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("영상 스트리밍 저장 중 용량 초과 시 부분 파일 삭제")
  void storeFile_videoStreamTooLarge_deletePartialFile() throws IOException {
    MultipartFile file = new StreamingMultipartFile(
        "file",
        "seminar.mp4",
        "video/mp4",
        mp4Bytes().length,
        uploadProperties.getVideoMaxSize().toBytes() + 1,
        mp4Bytes()
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
    try (Stream<Path> paths = Files.walk(tempDir)) {
      assertThat(paths.filter(Files::isRegularFile).toList()).isEmpty();
    }
  }

  @Test
  @DisplayName("정상 OOXML 구조 첨부파일 저장")
  void storeFile_docx_success() throws IOException {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "paper.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        minimalOoxml("docx")
    );

    FileUploadService.StoredFile storedFile = fileUploadService.storeFile(file);

    assertThat(storedFile.savedFilename()).matches(monthlyPattern("files", "docx"));
    assertThat(Files.exists(tempDir.resolve(storedFile.savedFilename()))).isTrue();
  }

  @Test
  @DisplayName("오피스 문서 위장 일반 압축 파일 거절")
  void storeFile_genericZipAsDocx_throwException() throws IOException {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "paper.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        zipWithEntry("payload.txt", "not an ooxml package")
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("매크로 포함 OOXML 첨부파일 거절")
  void storeFile_macroOoxml_throwException() throws IOException {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "paper.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        macroOoxml()
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("액티브X 또는 OLE 객체 포함 오피스 첨부파일 거절")
  void storeFile_activexOrOleOoxml_throwException() throws IOException {
    MockMultipartFile activeX = new MockMultipartFile(
        "file",
        "paper.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        activeXOoxml()
    );
    MockMultipartFile ole = new MockMultipartFile(
        "file",
        "paper.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        oleOoxml()
    );

    assertCustomException(
        () -> fileUploadService.storeFile(activeX),
        ErrorCode.INVALID_UPLOAD_FILE
    );
    assertCustomException(
        () -> fileUploadService.storeFile(ole),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("텍스트 파일 위장 실행 파일 바이트 거절")
  void storeFile_executableAsText_throwException() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "notes.txt",
        "text/plain",
        new byte[] {'M', 'Z', 0, 0, 0}
    );

    assertCustomException(
        () -> fileUploadService.storeFile(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("디코딩 가능한 PNG 이미지 크기 정보 저장")
  void storeImage_png_success() throws IOException {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "chart.png",
        "image/png",
        pngBytes(3, 2)
    );

    FileUploadService.StoredFile storedFile = fileUploadService.storeImage(file);

    assertThat(storedFile.savedFilename()).matches(monthlyPattern("images", "png"));
    assertThat(storedFile.width()).isEqualTo(3);
    assertThat(storedFile.height()).isEqualTo(2);
    assertThat(Files.exists(tempDir.resolve(storedFile.savedFilename()))).isTrue();
  }

  @Test
  @DisplayName("이미지 MIME/확장자 불일치 거절")
  void storeImage_mimeExtensionMismatch_throwException() throws IOException {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "chart.jpg",
        "image/png",
        pngBytes(1, 1)
    );

    assertCustomException(
        () -> fileUploadService.storeImage(file),
        ErrorCode.UNSUPPORTED_IMAGE_TYPE
    );
  }

  @Test
  @DisplayName("시그니처만 맞는 깨진 이미지 디코딩 거절")
  void storeImage_truncatedPng_throwException() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "chart.png",
        "image/png",
        new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
    );

    assertCustomException(
        () -> fileUploadService.storeImage(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("이미지 용량 제한 초과 거절")
  void storeImage_oversized_throwException() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "huge.png",
        "image/png",
        new byte[(int) uploadProperties.getImageMaxSize().toBytes() + 1]
    );

    assertCustomException(
        () -> fileUploadService.storeImage(file),
        ErrorCode.INVALID_UPLOAD_FILE
    );
  }

  @Test
  @DisplayName("저장 파일 삭제")
  void delete_success() throws IOException {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "delete-me.txt",
        "text/plain",
        "data".getBytes(StandardCharsets.UTF_8)
    );
    String savedFilename = fileUploadService.store(file);
    Path filePath = tempDir.resolve(savedFilename);

    assertThat(Files.exists(filePath)).isTrue();

    fileUploadService.delete(savedFilename);

    assertThat(Files.exists(filePath)).isFalse();
  }

  @Test
  @DisplayName("없는 파일 삭제 요청 예외 없음")
  void delete_nonExistingFile_noException() {
    assertDoesNotThrow(() -> fileUploadService.delete("non-existing-file.txt"));
  }

  private void assertCustomException(Runnable action, ErrorCode errorCode) {
    CustomException exception = assertThrows(CustomException.class, action::run);
    assertThat(exception.getErrorCode()).isEqualTo(errorCode);
  }

  private String monthlyPattern(String directory, String extension) {
    YearMonth now = YearMonth.now();
    return "%s/%d/%02d/[0-9a-f\\-]{36}\\.%s".formatted(
        directory,
        now.getYear(),
        now.getMonthValue(),
        extension
    );
  }

  private byte[] pngBytes(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  private byte[] mp4Bytes() {
    return new byte[] {
        0x00, 0x00, 0x00, 0x18,
        'f', 't', 'y', 'p',
        'i', 's', 'o', 'm',
        0x00, 0x00, 0x00, 0x00,
        'i', 's', 'o', 'm',
        'm', 'p', '4', '2'
    };
  }

  private byte[] webmBytes() {
    return new byte[] {
        0x1A, 0x45, (byte) 0xDF, (byte) 0xA3,
        0x42, (byte) 0x82, (byte) 0x84,
        'w', 'e', 'b', 'm'
    };
  }

  private byte[] movBytes() {
    return new byte[] {
        0x00, 0x00, 0x00, 0x14,
        'f', 't', 'y', 'p',
        'q', 't', ' ', ' ',
        0x00, 0x00, 0x00, 0x00,
        'q', 't', ' ', ' '
    };
  }

  private byte[] minimalOoxml(String extension) throws IOException {
    // 정상 OOXML 최소 패키지 생성
    String partName = switch (extension) {
      case "docx" -> "word/document.xml";
      case "xlsx" -> "xl/workbook.xml";
      case "pptx" -> "ppt/presentation.xml";
      default -> throw new IllegalArgumentException("Unsupported extension: " + extension);
    };
    return zipWithEntries(
        new ZipFileEntry("[Content_Types].xml", contentTypesXml(extension)),
        new ZipFileEntry(partName, "<root/>")
    );
  }

  private byte[] macroOoxml() throws IOException {
    // vbaProject 포함 매크로 문서 생성
    return zipWithEntries(
        new ZipFileEntry(
            "[Content_Types].xml",
            """
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Override PartName="/word/document.xml" ContentType="application/vnd.ms-word.document.macroEnabled.main+xml"/>
                </Types>
                """
        ),
        new ZipFileEntry("word/document.xml", "<root/>"),
        new ZipFileEntry("word/vbaProject.bin", "macro")
    );
  }

  private byte[] activeXOoxml() throws IOException {
    // ActiveX 객체 포함 문서 생성
    return zipWithEntries(
        new ZipFileEntry("[Content_Types].xml", contentTypesXml("docx")),
        new ZipFileEntry("word/document.xml", "<root/>"),
        new ZipFileEntry("word/activeX/activeX1.xml", "<root/>")
    );
  }

  private byte[] oleOoxml() throws IOException {
    // OLE 객체 포함 문서 생성
    return zipWithEntries(
        new ZipFileEntry("[Content_Types].xml", contentTypesXml("docx")),
        new ZipFileEntry("word/document.xml", "<root/>"),
        new ZipFileEntry("word/embeddings/oleObject1.bin", "ole")
    );
  }

  private String contentTypesXml(String extension) {
    String mainContentType = switch (extension) {
      case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
      case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
      case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
      default -> throw new IllegalArgumentException("Unsupported extension: " + extension);
    };
    return """
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Override PartName="/main.xml" ContentType="%s"/>
        </Types>
        """.formatted(mainContentType);
  }

  private byte[] zipWithEntry(String name, String content) throws IOException {
    return zipWithEntries(new ZipFileEntry(name, content));
  }

  private byte[] zipWithEntries(ZipFileEntry... entries) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      for (ZipFileEntry entry : entries) {
        zip.putNextEntry(new ZipEntry(entry.name()));
        zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }

  private record ZipFileEntry(String name, String content) {
  }

  private record SizedMultipartFile(
      String name,
      String originalFilename,
      String contentType,
      long size,
      byte[] bytes
  ) implements MultipartFile {

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getOriginalFilename() {
      return originalFilename;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return size <= 0;
    }

    @Override
    public long getSize() {
      return size;
    }

    @Override
    public byte[] getBytes() {
      return bytes;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) throws IOException {
      Files.write(dest.toPath(), bytes);
    }
  }

  private record StreamingMultipartFile(
      String name,
      String originalFilename,
      String contentType,
      long reportedSize,
      long streamSize,
      byte[] prefix
  ) implements MultipartFile {

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getOriginalFilename() {
      return originalFilename;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return reportedSize <= 0;
    }

    @Override
    public long getSize() {
      return reportedSize;
    }

    @Override
    public byte[] getBytes() {
      return prefix;
    }

    @Override
    public InputStream getInputStream() {
      return new PrefixThenZeroInputStream(prefix, streamSize);
    }

    @Override
    public void transferTo(File dest) {
      throw new UnsupportedOperationException("테스트 스트림 전용");
    }
  }

  private static class PrefixThenZeroInputStream extends InputStream {

    private final byte[] prefix;
    private final long size;
    private long position;

    private PrefixThenZeroInputStream(byte[] prefix, long size) {
      this.prefix = prefix;
      this.size = size;
    }

    @Override
    public int read() {
      if (position >= size) {
        return -1;
      }
      if (position < prefix.length) {
        return prefix[(int) position++] & 0xFF;
      }
      position++;
      return 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (position >= size) {
        return -1;
      }
      int readLength = (int) Math.min(length, size - position);
      int prefixLength = (int) Math.max(0, Math.min(prefix.length - position, readLength));
      if (prefixLength > 0) {
        System.arraycopy(prefix, (int) position, buffer, offset, prefixLength);
      }
      if (readLength > prefixLength) {
        Arrays.fill(buffer, offset + prefixLength, offset + readLength, (byte) 0);
      }
      position += readLength;
      return readLength;
    }
  }
}
