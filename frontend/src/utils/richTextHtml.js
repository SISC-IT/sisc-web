const SAFE_URL_PROTOCOLS = new Set(['http:', 'https:', 'mailto:']);
const SAFE_COLOR_PATTERN = /^#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6,8})$/;
const SAFE_FONT_FAMILIES = new Set([
  'Pretendard',
  'Noto Sans KR',
  'Apple SD Gothic Neo',
  'Arial',
  'Georgia',
  'Courier New',
]);
const SAFE_FONT_SIZES = new Set([12, 14, 16, 18, 20, 24, 28, 32, 40]);

const getBaseUrl = () => {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin;
  }
  return 'https://example.invalid';
};

export const escapeHtmlText = (value) => String(value || '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;');

export const escapeHtmlAttribute = (value) => String(value || '')
  .replace(/&/g, '&amp;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;');

export const sanitizeHref = (value) => {
  const raw = String(value || '').trim();
  if (!raw || raw.startsWith('//') || /[\u0000-\u001F\u007F<>"']/.test(raw)) {
    return null;
  }

  try {
    const parsed = new URL(raw, getBaseUrl());
    if (!SAFE_URL_PROTOCOLS.has(parsed.protocol)) return null;
    return escapeHtmlAttribute(parsed.href);
  } catch {
    return null;
  }
};

const sanitizeColor = (value) => {
  const raw = String(value || '').trim();
  return SAFE_COLOR_PATTERN.test(raw) ? raw.toLowerCase() : null;
};

const sanitizeFontFamily = (value) => {
  const raw = String(value || '')
    .trim()
    .replaceAll('"', '')
    .replaceAll("'", '')
    .split(',')[0]
    .trim();
  return SAFE_FONT_FAMILIES.has(raw) ? raw : null;
};

const sanitizeFontSize = (value) => {
  const size = Number.parseInt(String(value || '').trim(), 10);
  return Number.isInteger(size) && SAFE_FONT_SIZES.has(size) ? String(size) : null;
};

export const sanitizeMarkAttrs = (mark) => {
  if (!mark || typeof mark !== 'object') return null;

  const type = String(mark.type || '');

  if (type === 'link') {
    const href = sanitizeHref(mark.attrs?.href);
    return href ? { href } : null;
  }

  if (type === 'textStyle' || type === 'color' || type === 'highlight') {
    const styles = [];
    const color = sanitizeColor(mark.attrs?.color);
    const backgroundColor = sanitizeColor(mark.attrs?.backgroundColor);
    const fontFamily = sanitizeFontFamily(mark.attrs?.fontFamily);
    const fontSize = sanitizeFontSize(mark.attrs?.fontSize);

    if (color) styles.push(`color: ${color}`);
    if (backgroundColor) styles.push(`background-color: ${backgroundColor}`);
    if (fontFamily) styles.push(`font-family: ${fontFamily}`);
    if (fontSize) styles.push(`font-size: ${fontSize}px`);

    return styles.length > 0 ? { style: escapeHtmlAttribute(styles.join('; ')) } : null;
  }

  return null;
};

export const jsonToHtml = (contentJson) => {
  if (!contentJson || !Array.isArray(contentJson.content)) {
    return '<p></p>';
  }

  const renderNode = (node) => {
    if (!node) return '';

    if (node.type === 'text') {
      const text = escapeHtmlText(node.text || '');

      if (!Array.isArray(node.marks) || node.marks.length === 0) return text;

      let content = text;

      node.marks.forEach((mark) => {
        if (!mark || !mark.type) return;

        const t = String(mark.type || '');
        if (t === 'bold') content = `<strong>${content}</strong>`;
        if (t === 'italic') content = `<em>${content}</em>`;
        if (t === 'underline') content = `<u>${content}</u>`;
        if (t === 'strike' || t === 'strikeThrough' || t === 'strike_through') content = `<s>${content}</s>`;

        if (t === 'link') {
          const attrs = sanitizeMarkAttrs(mark);
          if (attrs?.href) {
            content = `<a href="${attrs.href}" target="_blank" rel="noopener noreferrer">${content}</a>`;
          }
        }

        if (t === 'textStyle' || t === 'color' || t === 'highlight') {
          const attrs = sanitizeMarkAttrs(mark);
          if (attrs?.style) {
            content = `<span style="${attrs.style}">${content}</span>`;
          }
        }
      });

      return content;
    }

    if (node.type === 'paragraph') {
      return `<p>${(node.content || []).map(renderNode).join('')}</p>`;
    }

    if (node.type === 'heading') {
      const level = Math.min(Math.max(Number(node.attrs?.level || 1), 1), 6);
      return `<h${level}>${(node.content || []).map(renderNode).join('')}</h${level}>`;
    }

    if (node.type === 'image') {
      const src = escapeHtmlAttribute(node.attrs?.src || '');
      const alt = escapeHtmlAttribute(node.attrs?.alt || '');
      const width = String(node.attrs?.width || '').trim();
      const height = String(node.attrs?.height || '').trim();
      const align = String(node.attrs?.align || 'left').trim();
      const alignStyle = align === 'center'
        ? 'display: block; margin-left: auto; margin-right: auto;'
        : align === 'right'
          ? 'display: block; margin-left: auto; margin-right: 0;'
          : 'display: block; margin-left: 0; margin-right: auto;';
      const style = ` style="${alignStyle}${width ? ` width: ${escapeHtmlAttribute(width)};` : ''}${height ? ` height: ${escapeHtmlAttribute(height)};` : ' height: auto;'}"`;
      const widthAttr = width ? ` width="${escapeHtmlAttribute(width)}"` : '';
      const heightAttr = height ? ` height="${escapeHtmlAttribute(height)}"` : '';
      const alignAttr = ` data-align="${escapeHtmlAttribute(align)}"`;
      return `<img src="${src}" alt="${alt}"${widthAttr}${heightAttr}${alignAttr}${style} />`;
    }

    if (Array.isArray(node.content)) {
      return node.content.map(renderNode).join('');
    }

    return '';
  };

  return contentJson.content.map(renderNode).join('') || '<p></p>';
};