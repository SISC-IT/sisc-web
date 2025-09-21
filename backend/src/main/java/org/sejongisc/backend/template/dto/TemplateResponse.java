package org.sejongisc.backend.template.dto;

import lombok.Builder;
import org.sejongisc.backend.template.entity.Template;

import java.util.List;


@Builder
public class TemplateResponse {
  private List<Template> templates;
  private Template template;
}
