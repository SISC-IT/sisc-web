package org.sejongisc.backend.stock.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sejongisc.backend.stock.entity.CompanyName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyNameRepository extends JpaRepository<CompanyName, String> {

  Optional<CompanyName> findByTicker(String ticker);

  List<CompanyName> findByTickerIn(Collection<String> tickers);
}

