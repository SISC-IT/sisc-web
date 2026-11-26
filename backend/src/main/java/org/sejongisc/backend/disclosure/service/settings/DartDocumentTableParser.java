package org.sejongisc.backend.disclosure.service.settings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

// OpenDART 공시 원문 HTML/XML 표 파서
@Component
public class DartDocumentTableParser {
  private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?");

  public ParsedDocument parse(String xmlOrHtml) {
    if (xmlOrHtml == null || xmlOrHtml.isBlank()) {
      return new ParsedDocument(Map.of(), List.of());
    }
    Document doc = xmlOrHtml.contains("<DOCUMENT") || xmlOrHtml.contains("<BODY")
        ? Jsoup.parse(xmlOrHtml, "", Parser.xmlParser())
        : Jsoup.parse(xmlOrHtml);

    Map<String, String> keyValues = new LinkedHashMap<>();
    Map<Element, List<TableRow>> tables = new LinkedHashMap<>();

    for (Element tr : doc.select("tr, TR")) {
      List<Element> cells = tr.children().stream()
          .filter(cell -> List.of("th", "td", "te", "tu").contains(cell.normalName()))
          .toList();
      if (cells.isEmpty()) {
        continue;
      }
      List<String> rowValues = cells.stream()
          .map(cell -> normalizeWhitespace(cell.text()))
          .toList();
      if (rowValues.stream().allMatch(String::isBlank)) {
        continue;
      }
      var spans = cells.stream().map(cell -> columnSpan(cell.attr("colspan"))).toList();
      tables.computeIfAbsent(tr.closest("table"), ignored -> new ArrayList<>())
          .add(new TableRow(rowValues, spans));
      if (rowValues.size() == 2) {
        putValueIfPresent(keyValues, compactKey(rowValues.get(0)), rowValues.get(1));
      } else if (rowValues.size() >= 3) {
        String combinedKey = compactKey(rowValues.get(0) + ":" + rowValues.get(1));
        putValueIfPresent(keyValues, combinedKey, rowValues.get(2));
        putValueIfPresent(keyValues, compactKey(rowValues.get(0)), rowValues.get(rowValues.size() - 1));
      }
    }
    var parsedTables = tables.entrySet().stream()
        .map(entry -> parseTable(entry.getKey(), entry.getValue()))
        .toList();
    return new ParsedDocument(keyValues, parsedTables);
  }

  private static ParsedTable parseTable(Element table, List<TableRow> rows) {
    if (table == null) {
      return new ParsedTable(rows, "", "", List.of());
    }
    Element copy = table.clone();
    copy.select("table").stream().filter(nested -> nested != copy).forEach(Element::remove);
    var unitCandidates = copy.select("caption, th, td, te, tu").stream()
        .map(cell -> normalizeWhitespace(cell.text()))
        .toList();
    return new ParsedTable(rows, normalizeWhitespace(copy.text()),
        precedingUnitContext(table), unitCandidates);
  }

  private static String precedingUnitContext(Element table) {
    if (table == null) {
      return "";
    }
    // A DART unit label can be in the table or in the paragraph directly above it.
    Element previous = table.previousElementSibling();
    if (previous != null && List.of("p", "div", "span").contains(previous.normalName())
        && previous.selectFirst("table") == null) {
      String preceding = normalizeWhitespace(previous.text());
      if (preceding.startsWith("단위") || preceding.startsWith("(단위")) {
        return preceding;
      }
    }
    return "";
  }

  private static void putValueIfPresent(Map<String, String> values, String key, String value) {
    if (!value.isBlank() && !List.of("-", "–", "—", "해당없음", "해당사항없음", "없음").contains(compactKey(value))) {
      values.putIfAbsent(key, value);
    }
  }

  private static int columnSpan(String value) {
    try {
      return Math.clamp(Integer.parseInt(value), 1, 1000);
    } catch (NumberFormatException ignored) {
      return 1;
    }
  }

  public static String compactKey(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.replaceAll("\\s+", "")
        .replace("ㆍ", "")
        .replace("·", "")
        .replace("(", "")
        .replace(")", "");
  }

  public static Optional<BigDecimal> parseNumber(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String trimmed = raw.trim();
    if ("-".equals(trimmed) || "해당사항없음".equals(trimmed)) {
      return Optional.empty();
    }
    Matcher matcher = NUMBER_PATTERN.matcher(trimmed);
    if (!matcher.find()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new BigDecimal(matcher.group().replace(",", "")));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  private static String normalizeWhitespace(String value) {
    return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
  }

  public record TableRow(List<String> cells, List<Integer> spans) {
    public int width() {
      return spans.stream().mapToInt(Integer::intValue).sum();
    }

    public String valueAtColumn(int column) {
      for (int i = 0; i < spans.size(); i++) {
        column -= spans.get(i);
        if (column < 0) {
          return cells.get(i);
        }
      }
      return "";
    }
  }

  public record ParsedTable(
      List<TableRow> rows, String unitContext, String precedingUnitContext,
      List<String> unitCandidates) {}

  public record ParsedDocument(
      Map<String, String> keyValues,
      List<ParsedTable> tables) {

    public Optional<String> findValue(String... keyFragments) {
      for (String fragment : keyFragments) {
        String target = compactKey(fragment);
        for (var entry : keyValues.entrySet()) {
          if (entry.getKey().contains(target) && !entry.getValue().isBlank()) {
            return Optional.of(entry.getValue());
          }
        }
      }
      return Optional.empty();
    }

    public Optional<List<String>> findRowContaining(String... fragments) {
      return tables.stream().flatMap(table -> table.rows().stream()).map(TableRow::cells)
          .filter(row -> matchesRow(row, fragments))
          .findFirst();
    }
  }

  static boolean matchesRow(List<String> row, String... fragments) {
    String label = compactKey(row.getFirst());
    for (String fragment : fragments) {
      if (label.contains(compactKey(fragment))) {
        return true;
      }
    }
    return false;
  }
}
