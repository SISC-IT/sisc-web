package org.sejongisc.backend.disclosure.entity;

public enum DisclosureCardCategory {
  PRELIMINARY_EARNINGS("잠정 실적"),
  CONFIRMED_EARNINGS("확정 실적"),
  SHAREHOLDER_RETURN("주주환원"),
  SEMICONDUCTOR_CAPEX("반도체 CAPEX");

  private final String displayName;

  DisclosureCardCategory(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
