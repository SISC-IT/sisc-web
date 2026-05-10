package org.sejongisc.backend.board.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.board.entity.PostMedia;
import org.sejongisc.backend.board.entity.PostMediaType;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMediaResponse {

  private UUID mediaId;
  private PostMediaType mediaType;
  private String originalFilename;
  private String savedFilename;
  private String publicPath;
  private String url;
  private String contentType;
  private Long fileSize;
  private Integer width;
  private Integer height;
  private Integer sortOrder;

  public static PostMediaResponse of(PostMedia media, String url) {
    return PostMediaResponse.builder()
        .mediaId(media.getMediaId())
        .mediaType(media.getMediaType())
        .originalFilename(media.getOriginalFilename())
        .savedFilename(media.getSavedFilename())
        .publicPath(media.getPublicPath())
        .url(url)
        .contentType(media.getContentType())
        .fileSize(media.getFileSize())
        .width(media.getWidth())
        .height(media.getHeight())
        .sortOrder(media.getSortOrder())
        .build();
  }
}
