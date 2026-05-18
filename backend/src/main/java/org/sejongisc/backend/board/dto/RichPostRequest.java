package org.sejongisc.backend.board.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sejongisc.backend.board.entity.PostContentFormat;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RichPostRequest {

  @NotNull(message = "게시판을 선택해주세요.")
  private UUID boardId;

  @NotBlank(message = "제목은 필수 항목입니다.")
  private String title;

  @Builder.Default
  private PostContentFormat contentFormat = PostContentFormat.TIPTAP_JSON;

  @NotNull(message = "본문 JSON은 필수 항목입니다.")
  private JsonNode contentJson;

  @NotBlank(message = "본문 HTML은 필수 항목입니다.")
  private String contentHtml;

  private String contentText;

  @Builder.Default
  private boolean anonymous = false;

  @Builder.Default
  private List<UUID> inlineMediaIds = List.of();

  @Builder.Default
  private List<UUID> attachmentIds = List.of();
}
