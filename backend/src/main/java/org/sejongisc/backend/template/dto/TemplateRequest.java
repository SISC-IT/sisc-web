package org.sejongisc.backend.template.dto;

import lombok.Getter;
import lombok.Setter;
import org.sejongisc.backend.user.entity.User;

import java.util.UUID;


@Getter
@Setter
public class TemplateRequest {

  //@Schema(hidden = true, description = "유저")
  private User user;

  //@Schema(hidden = true, description = "템플릿 ID")
  private UUID templateId;  // 템플릿 ID

  //@Schema(description = "템플릿 제목", defaultValue = "기술주 템플릿")
  private String title;

  //@Schema(description = "템플릿 설명", defaultValue = "기술주 템플릿입니다.")
  private String description;

  //@Schema(description = "템플릿 공개 여부", defaultValue = "false")
  private Boolean isPublic;
}
