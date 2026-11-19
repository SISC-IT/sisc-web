package org.sejongisc.backend.disclosure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

@Entity
@Table(name = "disclosure_company")
@Getter
@NoArgsConstructor
public class DisclosureCompany extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "company_id", nullable = false, updatable = false)
  private UUID companyId;

  @Column(name = "dart_corp_code", nullable = false, unique = true, length = 8)
  private String corpCode;

  @Column(name = "stock_code", length = 6)
  private String stockCode;

  @Column(name = "company_name", nullable = false, length = 200)
  private String companyName;

  @Column(name = "dart_corp_cls", length = 1)
  private String corporationClass;

  @Column(name = "induty_code", length = 20)
  private String indutyCode;

  @Column(name = "closing_month", nullable = false)
  private Integer closingMonth;

  public DisclosureCompany(String corpCode, String stockCode, String companyName,
      String corporationClass, int closingMonth) {
    this.corpCode = corpCode;
    this.stockCode = stockCode;
    this.companyName = companyName;
    this.corporationClass = corporationClass;
    this.closingMonth = closingMonth;
  }

  public void updateProfile(String stockCode, String companyName, String corporationClass,
      int closingMonth) {
    this.stockCode = stockCode;
    if (companyName != null && !companyName.isBlank()) {
      this.companyName = companyName;
    }
    this.corporationClass = corporationClass;
    this.closingMonth = closingMonth;
  }

  public void updateIndutyCode(String indutyCode) {
    if (indutyCode != null && !indutyCode.isBlank()) {
      this.indutyCode = indutyCode.trim();
    }
  }
}
