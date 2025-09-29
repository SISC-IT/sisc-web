package org.sejongisc.backend.board.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "attachments")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "file_id", columnDefinition = "uuid")
    private UUID fileId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", columnDefinition = "uuid", nullable = false)
    private Post post;
}
