package org.sejongisc.backend.admin.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.publicweb.dto.PublicPostMetadataRequest;
import org.sejongisc.backend.publicweb.dto.PublicPostMetadataResponse;
import org.sejongisc.backend.publicweb.service.PublicPostMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/public/posts")
public class AdminPublicPostController {

  private final PublicPostMetadataService publicPostMetadataService;

  @PatchMapping("/{postId}")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<PublicPostMetadataResponse> updatePublicMetadata(
      @PathVariable UUID postId,
      @RequestBody PublicPostMetadataRequest request
  ) {
    return ResponseEntity.ok(publicPostMetadataService.updatePublicMetadata(postId, request));
  }
}
