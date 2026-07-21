package org.sejongisc.backend.publicweb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostMedia;
import org.sejongisc.backend.board.entity.PostMediaType;
import org.sejongisc.backend.board.repository.PostAttachmentRepository;
import org.sejongisc.backend.board.repository.PostMediaRepository;
import org.sejongisc.backend.board.service.FileUploadService;
import org.sejongisc.backend.common.config.UploadProperties;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class PdfThumbnailServiceTest {

  @TempDir
  Path tempDir;

  @Mock
  private PostMediaRepository postMediaRepository;

  @Mock
  private PostAttachmentRepository postAttachmentRepository;

  @Mock
  private FileUploadService fileUploadService;

  private PdfThumbnailService pdfThumbnailService;
  private UploadProperties uploadProperties;

  @BeforeEach
  void setUp() {
    uploadProperties = new UploadProperties();
    uploadProperties.setAttachmentMaxSize(DataSize.ofMegabytes(30));
    pdfThumbnailService = new PdfThumbnailService(
        postMediaRepository,
        postAttachmentRepository,
        fileUploadService,
        uploadProperties
    );
  }

  @Test
  @DisplayName("용량 제한 초과 PDF 썸네일 생성 생략")
  void ensureThumbnail_oversizedPdf_returnsEmpty() throws IOException {
    UUID postId = UUID.randomUUID();
    Post post = Post.builder()
        .postId(postId)
        .build();
    Path pdf = tempDir.resolve("large.pdf");
    try (RandomAccessFile file = new RandomAccessFile(pdf.toFile(), "rw")) {
      file.setLength(uploadProperties.getAttachmentMaxSize().toBytes() + 1);
    }
    PostMedia pdfMedia = PostMedia.builder()
        .mediaType(PostMediaType.FILE_ATTACHMENT)
        .originalFilename("large.pdf")
        .contentType("application/pdf")
        .filePath(pdf.toString())
        .build();

    when(postMediaRepository.findFirstByPostPostIdAndMediaTypeOrderByCreatedDateAsc(
        postId,
        PostMediaType.THUMBNAIL
    )).thenReturn(Optional.empty());
    when(postMediaRepository.findAllByPostPostIdAndMediaTypeOrderBySortOrderAscCreatedDateAsc(
        postId,
        PostMediaType.FILE_ATTACHMENT
    )).thenReturn(List.of(pdfMedia));

    Optional<PostMedia> thumbnail = pdfThumbnailService.ensureThumbnail(post);

    assertThat(thumbnail).isEmpty();
    verify(postMediaRepository, never()).save(any(PostMedia.class));
  }
}
