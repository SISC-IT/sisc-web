package org.sejongisc.backend.board.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.sejongisc.backend.board.dto.RichPostRequest;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostContentFormat;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class PostContentService {

  private static final Pattern FONT_SIZE_PATTERN = Pattern.compile("^(?:[8-9]|[1-4][0-9]|50)px$");
  private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
  private static final Pattern RGB_COLOR_PATTERN = Pattern.compile(
      "^rgba?\\(\\s*(?:\\d{1,3}\\s*,\\s*){2}\\d{1,3}(?:\\s*,\\s*(?:0|1|0?\\.\\d+))?\\s*\\)$"
  );
  private static final List<String> TEXT_ALIGN_VALUES = List.of("left", "right", "center", "justify");

  private final ObjectMapper objectMapper;

  @Value("${app.upload.public-path-prefix:/uploads}")
  private String publicPathPrefix;

  @Value("${app.upload.public-base-url:}")
  private String publicBaseUrl;

  public NormalizedPostContent fromPlainText(String plainText) {
    String contentText = plainText == null ? "" : plainText;
    return new NormalizedPostContent(
        PostContentFormat.PLAIN_TEXT,
        contentText,
        null,
        plainTextToHtml(contentText),
        contentText
    );
  }

  public NormalizedPostContent fromRichRequest(RichPostRequest request) {
    validateTiptapJson(request.getContentJson());

    String serializedJson;
    try {
      serializedJson = objectMapper.writeValueAsString(request.getContentJson());
    } catch (JsonProcessingException e) {
      throw new CustomException(ErrorCode.INVALID_POST_CONTENT);
    }

    String sanitizedHtml = sanitizeHtml(request.getContentHtml());
    String contentText = StringUtils.hasText(request.getContentText())
        ? request.getContentText().trim()
        : Jsoup.parse(sanitizedHtml).text();

    return new NormalizedPostContent(
        PostContentFormat.TIPTAP_JSON,
        contentText,
        serializedJson,
        sanitizedHtml,
        contentText
    );
  }

  public JsonNode parseContentJson(String contentJson) {
    if (!StringUtils.hasText(contentJson)) {
      return null;
    }
    try {
      return objectMapper.readTree(contentJson);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  public PostContentFormat resolveFormat(Post post) {
    return post.getContentFormat() == null ? PostContentFormat.PLAIN_TEXT : post.getContentFormat();
  }

  public String resolveContentHtml(Post post) {
    if (resolveFormat(post) == PostContentFormat.TIPTAP_JSON && StringUtils.hasText(post.getContentHtml())) {
      return post.getContentHtml();
    }
    return plainTextToHtml(post.getContent());
  }

  public String resolveContentText(Post post) {
    if (StringUtils.hasText(post.getContentText())) {
      return post.getContentText();
    }
    return post.getContent();
  }

  public String sanitizeHtml(String html) {
    if (html == null) {
      return "";
    }

    Safelist safelist = Safelist.relaxed()
        .addTags("span", "u", "s", "mark", "figure", "figcaption", "hr",
            "table", "thead", "tbody", "tr", "th", "td")
        .addAttributes(":all", "style")
        .addAttributes("img", "src", "alt", "title", "width", "height")
        .addAttributes("a", "href", "title", "target", "rel")
        .addProtocols("a", "href", "http", "https", "mailto")
        .addProtocols("img", "src", "http", "https")
        .preserveRelativeLinks(true);

    String baseUri = StringUtils.hasText(publicBaseUrl) ? publicBaseUrl : "https://local.invalid";
    String cleaned = Jsoup.clean(html, baseUri, safelist);
    Document document = Jsoup.parseBodyFragment(cleaned);
    document.outputSettings().prettyPrint(false);

    for (Element element : document.body().getAllElements()) {
      sanitizeStyleAttribute(element);
      sanitizeLink(element);
    }

    for (Element image : document.select("img")) {
      if (!isAllowedImageSource(image.attr("src"))) {
        image.remove();
      }
    }

    return document.body().html();
  }

  private void validateTiptapJson(JsonNode contentJson) {
    if (contentJson == null
        || !contentJson.isObject()
        || !contentJson.has("type")
        || !"doc".equals(contentJson.get("type").asText())) {
      throw new CustomException(ErrorCode.INVALID_POST_CONTENT);
    }
  }

  private String plainTextToHtml(String plainText) {
    String safeText = plainText == null ? "" : plainText;
    String[] lines = safeText.split("\\R", -1);
    List<String> paragraphs = new ArrayList<>();
    for (String line : lines) {
      if (line.isEmpty()) {
        paragraphs.add("<p><br></p>");
      } else {
        paragraphs.add("<p>" + HtmlUtils.htmlEscape(line) + "</p>");
      }
    }
    return String.join("", paragraphs);
  }

  private void sanitizeStyleAttribute(Element element) {
    if (!element.hasAttr("style")) {
      return;
    }

    String sanitizedStyle = sanitizeStyle(element.attr("style"));
    if (sanitizedStyle.isBlank()) {
      element.removeAttr("style");
    } else {
      element.attr("style", sanitizedStyle);
    }
  }

  private String sanitizeStyle(String style) {
    List<String> declarations = new ArrayList<>();
    for (String declaration : style.split(";")) {
      int colonIndex = declaration.indexOf(':');
      if (colonIndex < 0) {
        continue;
      }

      String property = declaration.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
      String value = declaration.substring(colonIndex + 1).trim();
      String lowerValue = value.toLowerCase(Locale.ROOT);

      if (lowerValue.contains("url(")
          || lowerValue.contains("expression")
          || lowerValue.contains("javascript:")
          || lowerValue.contains("behavior:")
          || lowerValue.contains("-moz-binding")) {
        continue;
      }

      if (isAllowedStyle(property, value)) {
        declarations.add(property + ": " + value);
      }
    }
    return String.join("; ", declarations);
  }

  private boolean isAllowedStyle(String property, String value) {
    String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
    return switch (property) {
      case "font-size" -> FONT_SIZE_PATTERN.matcher(normalizedValue).matches();
      case "text-align" -> TEXT_ALIGN_VALUES.contains(normalizedValue);
      case "color", "background-color" -> isAllowedColor(normalizedValue);
      default -> false;
    };
  }

  private boolean isAllowedColor(String value) {
    return HEX_COLOR_PATTERN.matcher(value).matches()
        || RGB_COLOR_PATTERN.matcher(value).matches()
        || "transparent".equals(value);
  }

  private void sanitizeLink(Element element) {
    if (!"a".equals(element.tagName())) {
      return;
    }
    element.attr("rel", "noopener noreferrer");
    if ("_blank".equals(element.attr("target"))) {
      return;
    }
    element.removeAttr("target");
  }

  private boolean isAllowedImageSource(String src) {
    if (!StringUtils.hasText(src)) {
      return false;
    }

    String normalizedPrefix = normalizePath(publicPathPrefix);
    if (src.startsWith(normalizedPrefix + "/images/")) {
      return true;
    }

    if (StringUtils.hasText(publicBaseUrl)) {
      String baseUrl = publicBaseUrl.endsWith("/")
          ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
          : publicBaseUrl;
      return src.startsWith(baseUrl + normalizedPrefix + "/images/");
    }

    return false;
  }

  private String normalizePath(String path) {
    if (!StringUtils.hasText(path)) {
      return "/uploads";
    }
    return path.startsWith("/") ? path : "/" + path;
  }

  public record NormalizedPostContent(
      PostContentFormat contentFormat,
      String content,
      String contentJson,
      String contentHtml,
      String contentText
  ) {
  }
}
