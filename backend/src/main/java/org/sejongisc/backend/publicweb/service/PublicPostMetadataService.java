package org.sejongisc.backend.publicweb.service;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.repository.PostRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.publicweb.dto.PublicPostMetadataRequest;
import org.sejongisc.backend.publicweb.dto.PublicPostMetadataResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicPostMetadataService {

  private final PostRepository postRepository;
  private final PdfThumbnailService pdfThumbnailService;

  @Transactional
  public PublicPostMetadataResponse updatePublicMetadata(UUID postId, PublicPostMetadataRequest request) {
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    if (request.publicVisible() != null) {
      post.setPublicVisible(request.publicVisible());
    }
    if (request.publicPublishedAt() != null) {
      post.setPublicPublishedAt(request.publicPublishedAt());
    }
    if (post.isPublicVisible() && post.getPublicPublishedAt() == null) {
      post.setPublicPublishedAt(LocalDateTime.now());
    }
    if (post.isPublicVisible()) {
      pdfThumbnailService.ensureThumbnail(post);
    }

    String thumbnailUrl = pdfThumbnailService.findThumbnail(post.getPostId())
        .map(pdfThumbnailService::toPublicUrl)
        .orElse(null);

    return new PublicPostMetadataResponse(
        post.getPostId(),
        post.isPublicVisible(),
        post.getPublicPublishedAt(),
        thumbnailUrl
    );
  }
}
