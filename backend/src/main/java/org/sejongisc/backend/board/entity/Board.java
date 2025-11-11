package org.sejongisc.backend.board.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Board extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID boardId;

  private String boardName;

  @ManyToOne(fetch = FetchType.LAZY)
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_board_id")
  private Board parentBoard;
}
