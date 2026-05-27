package org.sejongisc.backend.publicweb.service;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dto.RichPostRequest;
import org.sejongisc.backend.board.entity.PostContentFormat;
import org.sejongisc.backend.board.service.PostContentService;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.publicweb.dto.PublicPageRequest;
import org.sejongisc.backend.publicweb.dto.PublicPageResponse;
import org.sejongisc.backend.publicweb.entity.PublicPage;
import org.sejongisc.backend.publicweb.entity.PublicPageType;
import org.sejongisc.backend.publicweb.repository.PublicPageRepository;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PublicPageService {

  private final PublicPageRepository publicPageRepository;
  private final PostContentService postContentService;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public PublicPageResponse getPublicPage(PublicPageType pageType) {
    PublicPage page = publicPageRepository.findByPageType(pageType)
        .orElseThrow(() -> new CustomException(ErrorCode.PUBLIC_PAGE_NOT_FOUND));
    return toResponse(page);
  }

  @Transactional
  public PublicPageResponse upsertPublicPage(PublicPageType pageType, PublicPageRequest request, UUID userId) {
    PublicPage page = publicPageRepository.findByPageType(pageType)
        .orElseGet(() -> PublicPage.builder().pageType(pageType).build());

    PostContentService.NormalizedPostContent content = normalizeContent(request);
    User updatedBy = userId == null
        ? null
        : userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    page.setTitle(request.title().trim());
    page.setContent(content.content());
    page.setContentFormat(content.contentFormat());
    page.setContentJson(content.contentJson());
    page.setContentHtml(content.contentHtml());
    page.setContentText(content.contentText());
    page.setPublishedAt(request.publishedAt() == null ? LocalDateTime.now() : request.publishedAt());
    page.setUpdatedBy(updatedBy);

    return toResponse(publicPageRepository.save(page));
  }

  private PostContentService.NormalizedPostContent normalizeContent(PublicPageRequest request) {
    PostContentFormat contentFormat = request.contentFormat() == null
        ? inferContentFormat(request)
        : request.contentFormat();

    if (contentFormat == PostContentFormat.TIPTAP_JSON) {
      RichPostRequest richRequest = RichPostRequest.builder()
          .title(request.title())
          .contentFormat(PostContentFormat.TIPTAP_JSON)
          .contentJson(request.contentJson())
          .contentHtml(request.contentHtml())
          .contentText(request.contentText())
          .build();
      return postContentService.fromRichRequest(richRequest);
    }

    if (!StringUtils.hasText(request.content())) {
      throw new CustomException(ErrorCode.INVALID_POST_CONTENT);
    }
    return postContentService.fromPlainText(request.content());
  }

  private PostContentFormat inferContentFormat(PublicPageRequest request) {
    if (request.contentJson() != null || StringUtils.hasText(request.contentHtml())) {
      return PostContentFormat.TIPTAP_JSON;
    }
    return PostContentFormat.PLAIN_TEXT;
  }

  private PublicPageResponse toResponse(PublicPage page) {
    return new PublicPageResponse(
        page.getPublicPageId(),
        page.getPageType(),
        page.getTitle(),
        page.getContentFormat(),
        postContentService.parseContentJson(page.getContentJson()),
        page.getContentHtml(),
        page.getContentText(),
        page.getPublishedAt()
    );
  }
}
