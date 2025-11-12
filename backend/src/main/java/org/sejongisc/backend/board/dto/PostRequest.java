package org.sejongisc.backend.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PostRequest {

  @NotNull(message = "게시판을 선택해주세요.")
  private UUID boardId;

  @NotBlank(message = "제목은 필수 항목입니다.")
  private String title;

  @NotBlank(message = "내용은 필수 항목입니다.")
  private String content;

  private List<MultipartFile> files;
}
