package org.sejongisc.backend.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.publicweb.dto.PublicPageRequest;
import org.sejongisc.backend.publicweb.dto.PublicPageResponse;
import org.sejongisc.backend.publicweb.entity.PublicPageType;
import org.sejongisc.backend.publicweb.service.PublicPageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/public-pages")
public class AdminPublicPageController {

  private final PublicPageService publicPageService;

  @GetMapping("/{pageType}")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<PublicPageResponse> getPublicPage(@PathVariable PublicPageType pageType) {
    return ResponseEntity.ok(publicPageService.getPublicPage(pageType));
  }

  @PutMapping("/{pageType}")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<PublicPageResponse> upsertPublicPage(
      @PathVariable PublicPageType pageType,
      @RequestBody @Valid PublicPageRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails
  ) {
    UUID userId = customUserDetails == null ? null : customUserDetails.getUserId();
    return ResponseEntity.ok(publicPageService.upsertPublicPage(pageType, request, userId));
  }
}
