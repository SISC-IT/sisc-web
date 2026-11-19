package org.sejongisc.backend.disclosure.controller;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.service.DisclosureEarningsQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disclosures/companies")
@RequiredArgsConstructor
public class DisclosureEarningsController {
  private final DisclosureEarningsQueryService query;

  @GetMapping("/{corpCode}/financials")
  public ResponseEntity<DisclosureEarningsResponse> financials(@PathVariable("corpCode") String corpCode) {
    if (!corpCode.matches("[0-9]{8}")) {
      throw new CustomException(ErrorCode.INVALID_INPUT);
    }
    return ResponseEntity.of(query.latest(corpCode));
  }
}
