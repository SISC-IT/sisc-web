package org.sejongisc.backend.publicweb.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostAttachment;
import org.sejongisc.backend.board.entity.PostMedia;
import org.sejongisc.backend.board.entity.PostMediaType;
import org.sejongisc.backend.board.repository.PostAttachmentRepository;
import org.sejongisc.backend.board.repository.PostMediaRepository;
import org.sejongisc.backend.board.service.FileUploadService;
import org.sejongisc.backend.common.config.UploadProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfThumbnailService {

  private static final int THUMBNAIL_DPI = 144;

  private final PostMediaRepository postMediaRepository;
  private final PostAttachmentRepository postAttachmentRepository;
  private final FileUploadService fileUploadService;
  private final UploadProperties uploadProperties;

  @Transactional
  public Optional<PostMedia> ensureThumbnail(Post post) {
    if (post == null || post.getPostId() == null) {
      return Optional.empty();
    }

    Optional<PostMedia> existingThumbnail = findThumbnail(post.getPostId());
    if (existingThumbnail.isPresent()) {
      return existingThumbnail;
    }

    Optional<PdfSource> source = findFirstPdfSource(post.getPostId());
    if (source.isEmpty()) {
      return Optional.empty();
    }

    try {
      PostMedia thumbnail = renderAndSave(post, source.get());
      return Optional.of(thumbnail);
    } catch (Exception e) {
      // 썸네일 생성 실패 시 게시글 조회/공개 흐름 유지
      log.warn("PDF thumbnail generation failed. postId={}, source={}", post.getPostId(), source.get().filePath(), e);
      return Optional.empty();
    }
  }

  @Transactional(readOnly = true)
  public Optional<PostMedia> findThumbnail(UUID postId) {
    return postMediaRepository.findFirstByPostPostIdAndMediaTypeOrderByCreatedDateAsc(
        postId,
        PostMediaType.THUMBNAIL
    );
  }

  public String toPublicUrl(PostMedia media) {
    if (media == null) {
      return null;
    }
    return fileUploadService.buildPublicUrl(media.getPublicPath());
  }

  private Optional<PdfSource> findFirstPdfSource(UUID postId) {
    List<PostMedia> mediaAttachments = postMediaRepository
        .findAllByPostPostIdAndMediaTypeOrderBySortOrderAscCreatedDateAsc(postId, PostMediaType.FILE_ATTACHMENT);

    Optional<PdfSource> mediaSource = mediaAttachments.stream()
        .filter(this::isPdf)
        .filter(media -> StringUtils.hasText(media.getFilePath()))
        .findFirst()
        .map(media -> new PdfSource(media.getFilePath(), media.getOriginalFilename()));
    if (mediaSource.isPresent()) {
      return mediaSource;
    }

    return postAttachmentRepository.findAllByPostPostId(postId)
        .stream()
        .filter(this::isPdf)
        .filter(attachment -> StringUtils.hasText(attachment.getFilePath()))
        .findFirst()
        .map(attachment -> new PdfSource(attachment.getFilePath(), attachment.getOriginalFilename()));
  }

  private PostMedia renderAndSave(Post post, PdfSource source) throws IOException {
    Path sourcePath = Path.of(source.filePath()).toAbsolutePath().normalize();
    if (!Files.exists(sourcePath)) {
      throw new IOException("PDF source file does not exist: " + sourcePath);
    }
    // 대용량 PDF 렌더링 건너뜀
    if (Files.size(sourcePath) > uploadProperties.getAttachmentMaxSize().toBytes()) {
      throw new IOException("PDF source file is too large for thumbnail generation: " + sourcePath);
    }

    try (PDDocument document = Loader.loadPDF(sourcePath.toFile())) {
      if (document.getNumberOfPages() < 1) {
        throw new IOException("PDF has no pages: " + sourcePath);
      }

      PDFRenderer renderer = new PDFRenderer(document);
      BufferedImage image = renderer.renderImageWithDPI(0, THUMBNAIL_DPI, ImageType.RGB);
      String savedFilename = buildThumbnailFilename();
      Path destination = fileUploadService.getRootLocation().resolve(savedFilename).normalize();
      Files.createDirectories(destination.getParent());

      if (!ImageIO.write(image, "jpg", destination.toFile())) {
        throw new IOException("JPEG writer is not available");
      }

      long fileSize = Files.size(destination);
      PostMedia thumbnail = PostMedia.builder()
          .post(post)
          .uploadedBy(post.getUser())
          .mediaType(PostMediaType.THUMBNAIL)
          .savedFilename(savedFilename)
          .originalFilename("thumbnail-" + source.originalFilename())
          .filePath(destination.toString())
          .publicPath(fileUploadService.buildPublicPath(savedFilename))
          .contentType("image/jpeg")
          .fileSize(fileSize)
          .width(image.getWidth())
          .height(image.getHeight())
          .sortOrder(0)
          .build();
      return postMediaRepository.save(thumbnail);
    }
  }

  private String buildThumbnailFilename() {
    YearMonth now = YearMonth.now();
    return "thumbnails/%d/%02d/%s.jpg".formatted(
        now.getYear(),
        now.getMonthValue(),
        UUID.randomUUID()
    );
  }

  private boolean isPdf(PostMedia media) {
    return isPdf(media.getContentType(), media.getOriginalFilename());
  }

  private boolean isPdf(PostAttachment attachment) {
    return isPdf(null, attachment.getOriginalFilename());
  }

  private boolean isPdf(String contentType, String filename) {
    if (StringUtils.hasText(contentType) && "application/pdf".equalsIgnoreCase(contentType.trim())) {
      return true;
    }
    return StringUtils.hasText(filename) && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
  }

  private record PdfSource(String filePath, String originalFilename) {
  }
}
