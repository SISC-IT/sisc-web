package org.sejongisc.backend.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "company_names")
public class CompanyName {

  @Id
  @Column(name = "ticker", nullable = false, length = 255)
  private String ticker;

  @Column(name = "company_name", nullable = false, length = 100)
  private String companyName;
}
