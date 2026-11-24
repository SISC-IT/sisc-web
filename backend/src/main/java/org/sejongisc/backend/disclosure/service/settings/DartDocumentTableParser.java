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
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

// OpenDART 공시 원문 HTML/XML 표 파서
@Component
public class DartDocumentTableParser {
  private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?");

  public ParsedDocument parse(String xmlOrHtml) {
    if (xmlOrHtml == null || xmlOrHtml.isBlank()) {
      return new ParsedDocument(Map.of(), List.of(), "");
    }
    Document doc = xmlOrHtml.contains("<DOCUMENT") || xmlOrHtml.contains("<BODY")
        ? Jsoup.parse(xmlOrHtml, "", Parser.xmlParser())
        : Jsoup.parse(xmlOrHtml);

    Map<String, String> keyValues = new LinkedHashMap<>();
    List<List<String>> rows = new ArrayList<>();

    Elements trs = doc.select("tr, TR");
    for (Element tr : trs) {
      Elements cells = tr.select("th, td, TH, TD");
      if (cells.isEmpty()) {
        continue;
      }
      List<String> rowValues = cells.stream()
          .map(cell -> normalizeWhitespace(cell.text()))
          .filter(text -> !text.isBlank())
          .toList();
      if (rowValues.isEmpty()) {
        continue;
      }
      rows.add(rowValues);
      if (rowValues.size() == 2) {
        keyValues.putIfAbsent(compactKey(rowValues.get(0)), rowValues.get(1));
      } else if (rowValues.size() >= 3) {
        String combinedKey = compactKey(rowValues.get(0) + ":" + rowValues.get(1));
        keyValues.putIfAbsent(combinedKey, rowValues.get(2));
        keyValues.putIfAbsent(compactKey(rowValues.get(0)), rowValues.get(rowValues.size() - 1));
      }
    }
    return new ParsedDocument(keyValues, rows, normalizeWhitespace(doc.text()));
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

  public record ParsedDocument(
      Map<String, String> keyValues,
      List<List<String>> rows,
      String plainText) {

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
      for (List<String> row : rows) {
        String joined = compactKey(String.join("", row));
        for (String fragment : fragments) {
          if (joined.contains(compactKey(fragment))) {
            return Optional.of(row);
          }
        }
      }
      return Optional.empty();
    }
  }
}
