package org.sejongisc.backend.backtest.repository;

import org.sejongisc.backend.backtest.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID> {
  List<Template> findAllByUserUserId(UUID userId);
}
