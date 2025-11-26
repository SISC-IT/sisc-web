package org.sejongisc.backend.board.repository.projection;

import java.util.UUID;

public interface PostIdUserIdProjection {
    UUID getPostId();
    UUID getUserId();
}
