package org.sejongisc.backend.user.service.projection;

import java.util.UUID;

public interface UserIdNameProjection {
    UUID getUserId();
    String getName();
    String getEmail();
}
