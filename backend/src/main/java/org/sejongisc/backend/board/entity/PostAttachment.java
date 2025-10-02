package org.sejongisc.backend.board.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "post_attachment")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PostAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", columnDefinition = "uuid", nullable = false)
    private Post post;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String url;

    @Column(name = "mime_type")
    private String mimeType;
}
