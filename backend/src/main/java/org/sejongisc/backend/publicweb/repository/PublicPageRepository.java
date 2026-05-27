package org.sejongisc.backend.publicweb.repository;

import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.publicweb.entity.PublicPage;
import org.sejongisc.backend.publicweb.entity.PublicPageType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicPageRepository extends JpaRepository<PublicPage, UUID> {

  Optional<PublicPage> findByPageType(PublicPageType pageType);
}
