package org.sejongisc.backend.disclosure.entity;

public enum DisclosureCardCategory {
  ORDER_CONTRACT("수주 공시"),
  SHAREHOLDER_RETURN("주주환원"),
  CAPEX("CAPEX"),
  PRELIMINARY_EARNINGS("잠정 실적");

  private final String displayName;

  DisclosureCardCategory(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
