package org.sejongisc.backend.publicweb.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostAttachment;
import org.sejongisc.backend.board.entity.PostMedia;
import org.sejongisc.backend.board.entity.PostMediaType;
import org.sejongisc.backend.board.repository.PostAttachmentRepository;
import org.sejongisc.backend.board.repository.PostMediaRepository;
import org.sejongisc.backend.board.repository.PostRepository;
import org.sejongisc.backend.board.service.FileUploadService;
import org.sejongisc.backend.board.service.PostContentService;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.publicweb.dto.PublicPostDetailResponse;
import org.sejongisc.backend.publicweb.dto.PublicPostFileResponse;
import org.sejongisc.backend.publicweb.dto.PublicPostSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PublicPostService {

  private static final int MAX_PAGE_SIZE = 30;

  private final PostRepository postRepository;
  private final PostMediaRepository postMediaRepository;
  private final PostAttachmentRepository postAttachmentRepository;
  private final FileUploadService fileUploadService;
  private final PostContentService postContentService;
  private final PdfThumbnailService pdfThumbnailService;

  @Transactional(readOnly = true)
  public Page<PublicPostSummaryResponse> getPublicPosts(
      int page,
      int size,
      String keyword
  ) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    return postRepository.findPublicPosts(
            normalizeKeyword(keyword),
            PageRequest.of(safePage, safeSize)
        )
        .map(this::toSummaryResponse);
  }

  @Transactional(readOnly = true)
  public PublicPostDetailResponse getPublicPostDetail(UUID postId) {
    Post post = postRepository.findByPostIdAndPublicVisibleTrue(postId)
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    List<PublicPostFileResponse> pdfAttachments = getPdfAttachments(postId);
    List<PublicPostFileResponse> fileAttachments = getFileAttachments(postId);

    return new PublicPostDetailResponse(
        post.getPostId(),
        post.getTitle(),
        authorName(post),
        postContentService.resolveContentHtml(post),
        postContentService.resolveContentText(post),
        thumbnailUrl(post.getPostId()),
        pdfAttachments,
        fileAttachments
    );
  }

  private PublicPostSummaryResponse toSummaryResponse(Post post) {
    List<PublicPostFileResponse> pdfAttachments = getPdfAttachments(post.getPostId());
    LocalDateTime publishedAt = publicPublishedAt(post);
    return new PublicPostSummaryResponse(
        post.getPostId(),
        post.getTitle(),
        authorName(post),
        publishedAt,
        relativeTime(publishedAt),
        thumbnailUrl(post.getPostId()),
        !pdfAttachments.isEmpty(),
        pdfAttachments.size()
    );
  }

  private List<PublicPostFileResponse> getPdfAttachments(UUID postId) {
    return allAttachments(postId)
        .stream()
        .filter(this::isPdf)
        .toList();
  }

  private List<PublicPostFileResponse> getFileAttachments(UUID postId) {
    return allAttachments(postId)
        .stream()
        .filter(attachment -> !isPdf(attachment))
        .toList();
  }

  private List<PublicPostFileResponse> allAttachments(UUID postId) {
    List<PublicPostFileResponse> mediaAttachments = postMediaRepository
        .findAllByPostPostIdAndMediaTypeOrderBySortOrderAscCreatedDateAsc(postId, PostMediaType.FILE_ATTACHMENT)
        .stream()
        .map(this::toFileResponse)
        .toList();

    List<PublicPostFileResponse> legacyAttachments = postAttachmentRepository.findAllByPostPostId(postId)
        .stream()
        .map(this::toFileResponse)
        .toList();

    return java.util.stream.Stream.concat(mediaAttachments.stream(), legacyAttachments.stream()).toList();
  }

  private PublicPostFileResponse toFileResponse(PostMedia media) {
    return new PublicPostFileResponse(
        media.getMediaId(),
        media.getOriginalFilename(),
        fileUploadService.buildPublicUrl(media.getPublicPath()),
        media.getContentType(),
        media.getFileSize()
    );
  }

  private PublicPostFileResponse toFileResponse(PostAttachment attachment) {
    return new PublicPostFileResponse(
        attachment.getPostAttachmentId(),
        attachment.getOriginalFilename(),
        fileUploadService.buildPublicUrl(fileUploadService.buildPublicPath(attachment.getSavedFilename())),
        contentTypeFor(attachment.getOriginalFilename()),
        legacyFileSize(attachment)
    );
  }

  private String thumbnailUrl(UUID postId) {
    return pdfThumbnailService.findThumbnail(postId)
        .map(pdfThumbnailService::toPublicUrl)
        .orElse(null);
  }

  private String authorName(Post post) {
    if (post.isAnonymous() || post.getUser() == null) {
      return "익명";
    }
    return post.getUser().getName();
  }

  private LocalDateTime publicPublishedAt(Post post) {
    return post.getPublicPublishedAt() == null ? post.getCreatedDate() : post.getPublicPublishedAt();
  }

  private String relativeTime(LocalDateTime target) {
    if (target == null) {
      return "";
    }
    Duration duration = Duration.between(target, LocalDateTime.now());
    if (duration.isNegative() || duration.toMinutes() < 1) {
      return "방금 전";
    }
    if (duration.toHours() < 1) {
      return duration.toMinutes() + "분 전";
    }
    if (duration.toDays() < 1) {
      return duration.toHours() + "시간 전";
    }
    if (duration.toDays() < 7) {
      return duration.toDays() + "일 전";
    }
    if (duration.toDays() < 31) {
      return (duration.toDays() / 7) + "주 전";
    }
    if (duration.toDays() < 365) {
      return (duration.toDays() / 30) + "개월 전";
    }
    return (duration.toDays() / 365) + "년 전";
  }

  private String normalizeKeyword(String keyword) {
    return StringUtils.hasText(keyword) ? keyword.trim() : "";
  }

  private boolean isPdf(PublicPostFileResponse attachment) {
    return isPdf(attachment.contentType(), attachment.filename());
  }

  private boolean isPdf(String contentType, String filename) {
    if (StringUtils.hasText(contentType) && "application/pdf".equalsIgnoreCase(contentType.trim())) {
      return true;
    }
    return StringUtils.hasText(filename) && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
  }

  private String contentTypeFor(String filename) {
    return isPdf(null, filename) ? "application/pdf" : "application/octet-stream";
  }

  private Long legacyFileSize(PostAttachment attachment) {
    if (!StringUtils.hasText(attachment.getFilePath())) {
      return null;
    }
    try {
      return Files.size(Path.of(attachment.getFilePath()));
    } catch (IOException e) {
      return null;
    }
  }

}
