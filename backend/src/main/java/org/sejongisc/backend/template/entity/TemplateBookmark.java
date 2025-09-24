package org.sejongisc.backend.template.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;


@Entity
@Table(
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"template_id", "user_id"})
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateBookmark extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id")
  private Template template;

  //@ManyToOne(fetch = FetchType.LAZY)
  //@JoinColumn(name = "template_id")
  //private User user;
}
