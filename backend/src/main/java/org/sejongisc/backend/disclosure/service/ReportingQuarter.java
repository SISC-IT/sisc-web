package org.sejongisc.backend.disclosure.service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;

record ReportingQuarter(YearMonth end, int number) implements Comparable<ReportingQuarter> {
  private static final List<String> REPORT_CODES = List.of("11013", "11012", "11014", "11011");
  private static final Pattern TITLE = Pattern.compile(
      "^(?:\\[[^\\]]+\\]\\s*)*(사업보고서|반기보고서|분기보고서)\\s*\\((\\d{4})\\.(0[1-9]|1[0-2])\\)\\s*$");

  static Optional<ReportingQuarter> fromTitle(String title, int closingMonth) {
    return parse(title).flatMap(report -> {
      int offset = Math.floorMod(report.end().getMonthValue() - closingMonth, 12);
      int quarter = offset == 0 ? 4 : offset / 3;
      return offset % 3 == 0 && report.matches(quarter)
          ? Optional.of(new ReportingQuarter(report.end(), quarter)) : Optional.empty();
    });
  }

  static Optional<ReportingQuarter> fromFiling(DisclosureFiling filing) {
    int quarter = filing.getReportCode() == null ? 0 : REPORT_CODES.indexOf(filing.getReportCode()) + 1;
    return parse(filing.getTitle()).filter(report -> report.matches(quarter))
        .map(report -> new ReportingQuarter(report.end(), quarter));
  }

  static boolean isCorrection(String title) {
    return title.stripLeading().startsWith("[")
        && (title.contains("정정]") || title.contains("첨부추가]"));
  }

  static boolean canUse(String receiptNo, Map<String, String> titles) {
    if (!isCorrection(titles.get(receiptNo))) {
      return true;
    }
    return titles.entrySet().stream()
        .filter(entry -> entry.getKey().compareTo(receiptNo) < 0 && !isCorrection(entry.getValue()))
        .count() == 1;
  }

  private static Optional<ReportTitle> parse(String title) {
    if (title == null) {
      return Optional.empty();
    }
    var match = TITLE.matcher(title.strip());
    return match.matches() ? Optional.of(new ReportTitle(match.group(1),
        YearMonth.of(Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)))))
        : Optional.empty();
  }

  String reportCode() {
    return REPORT_CODES.get(number - 1);
  }

  int fiscalYear() {
    return end.plusMonths((4 - number) * 3L).getYear();
  }

  ReportingQuarter previous(int count) {
    return new ReportingQuarter(end.minusMonths(3L * count), Math.floorMod(number - 1 - count, 4) + 1);
  }

  List<ReportingQuarter> recentFour() {
    return IntStream.range(0, 4).mapToObj(this::previous).toList().reversed();
  }

  @Override
  public int compareTo(ReportingQuarter other) {
    int byEnd = end.compareTo(other.end);
    return byEnd != 0 ? byEnd : Integer.compare(number, other.number);
  }

  private record ReportTitle(String name, YearMonth end) {
    boolean matches(int quarter) {
      return switch (quarter) {
        case 1, 3 -> "분기보고서".equals(name);
        case 2 -> "반기보고서".equals(name);
        case 4 -> "사업보고서".equals(name);
        default -> false;
      };
    }
  }
}
