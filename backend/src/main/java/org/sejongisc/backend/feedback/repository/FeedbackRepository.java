package org.sejongisc.backend.feedback.repository;

import java.util.UUID;
import org.sejongisc.backend.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
}
