package org.sejongisc.backend.point.repository;

import org.sejongisc.backend.point.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionalRepository extends JpaRepository<PointTransaction, UUID> {
}
