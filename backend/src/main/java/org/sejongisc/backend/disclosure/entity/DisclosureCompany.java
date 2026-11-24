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

  @Column(name = "is_tracked", nullable = false)
  private boolean tracked = true;

  @Column(name = "track_capex", nullable = false)
  private boolean trackCapex = false;

  @Column(name = "closing_month", nullable = false)
  private Integer closingMonth;

  public DisclosureCompany(String corpCode, String stockCode, String companyName,
      String corporationClass, int closingMonth) {
    this.corpCode = corpCode;
    this.stockCode = stockCode;
    this.companyName = companyName;
    this.corporationClass = corporationClass;
    this.closingMonth = closingMonth;
    this.tracked = true;
    this.trackCapex = isDefaultCapexCorp(corpCode);
  }

  public void updateProfile(String stockCode, String companyName, String corporationClass,
      int closingMonth) {
    this.stockCode = stockCode;
    if (companyName != null && !companyName.isBlank()) {
      this.companyName = companyName;
    }
    this.corporationClass = corporationClass;
    this.closingMonth = closingMonth;
    if (isDefaultCapexCorp(this.corpCode)) {
      this.trackCapex = true;
    }
  }

  public void updateIndutyCode(String indutyCode) {
    if (indutyCode != null && !indutyCode.isBlank()) {
      this.indutyCode = indutyCode.trim();
    }
  }

  public void setTrackCapex(boolean trackCapex) {
    this.trackCapex = trackCapex;
  }

  public void setTracked(boolean tracked) {
    this.tracked = tracked;
  }

  public static boolean isDefaultCapexCorp(String corpCode) {
    // 삼성전자(00126380), SK하이닉스(00164779) 기본 활성화; 추후 현대차 등은 DB 플래그(track_capex)로 확장
    return "00126380".equals(corpCode) || "00164779".equals(corpCode);
  }
}
