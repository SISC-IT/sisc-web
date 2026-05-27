package org.sejongisc.backend.publicweb.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.publicweb.dto.PublicPageResponse;
import org.sejongisc.backend.publicweb.dto.PublicPostDetailResponse;
import org.sejongisc.backend.publicweb.dto.PublicPostSummaryResponse;
import org.sejongisc.backend.publicweb.entity.PublicPageType;
import org.sejongisc.backend.publicweb.service.PublicPageService;
import org.sejongisc.backend.publicweb.service.PublicPostService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public")
public class PublicPostController {

  private final PublicPostService publicPostService;
  private final PublicPageService publicPageService;

  @GetMapping("/posts")
  public ResponseEntity<Page<PublicPostSummaryResponse>> getPublicPosts(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "8") int size,
      @RequestParam(required = false) String keyword
  ) {
    return ResponseEntity.ok(publicPostService.getPublicPosts(page, size, keyword));
  }

  @GetMapping("/posts/{postId}")
  public ResponseEntity<PublicPostDetailResponse> getPublicPostDetail(@PathVariable UUID postId) {
    return ResponseEntity.ok(publicPostService.getPublicPostDetail(postId));
  }

  @GetMapping("/club")
  public ResponseEntity<PublicPageResponse> getClub() {
    return ResponseEntity.ok(publicPageService.getPublicPage(PublicPageType.CLUB));
  }

  @GetMapping("/executives")
  public ResponseEntity<PublicPageResponse> getExecutives() {
    return ResponseEntity.ok(publicPageService.getPublicPage(PublicPageType.EXECUTIVES));
  }
}
