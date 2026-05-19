import React, { useEffect, useRef } from 'react';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import { TextStyle } from '@tiptap/extension-text-style';
import { Color } from '@tiptap/extension-color';
import Highlight from '@tiptap/extension-highlight';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import TextAlign from '@tiptap/extension-text-align';
import styles from './PostHtmlView.module.css';
import { toAbsoluteImageUrl } from '../../../utils/imageUtils';
import { Extension } from '@tiptap/core';

// image utils imported from utils/imageUtils

const transformHtmlImages = (html = '') => {
  if (!html) return '<p></p>';

  // If the payload is a JSON string (tiptap json) or an object, convert to HTML first
  const tryParseJson = (value) => {
    if (!value) return null;
    if (typeof value === 'object') return value;
    const s = String(value || '').trim();
    if (!s) return null;
    if (s.startsWith('{') || s.startsWith('[')) {
      try {
        return JSON.parse(s);
      } catch {
        return null;
      }
    }
    return null;
  };

  const json = tryParseJson(html);
  if (json && typeof json === 'object') {
    // convert tiptap-like JSON to minimal HTML
    const renderNode = (node) => {
      if (!node) return '';
      if (node.type === 'text') return (node.text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      if (node.type === 'paragraph') return `<p>${(node.content || []).map(renderNode).join('')}</p>`;
      if (node.type === 'heading') {
        const level = Math.min(Math.max(Number(node.attrs?.level || 1), 1), 6);
        return `<h${level}>${(node.content || []).map(renderNode).join('')}</h${level}>`;
      }
      if (node.type === 'image') {
        const src = String(node.attrs?.src || '').replace(/"/g, '&quot;');
        const alt = String(node.attrs?.alt || '').replace(/"/g, '&quot;');
        const width = String(node.attrs?.width || '').trim();
        const height = String(node.attrs?.height || '').trim();
        const align = String(node.attrs?.align || 'left').trim();
        const widthAttr = width ? ` width="${width.replace(/"/g, '&quot;')}"` : '';
        const heightAttr = height ? ` height="${height.replace(/"/g, '&quot;')}"` : '';
        const style = align === 'center' ? ' style="display:block;margin-left:auto;margin-right:auto;"' : align === 'right' ? ' style="display:block;margin-left:auto;margin-right:0;"' : ' style="display:block;margin-left:0;margin-right:auto;"';
        return `<img src="${src}" alt="${alt}"${widthAttr}${heightAttr}${style} />`;
      }
      if (Array.isArray(node.content)) return node.content.map(renderNode).join('');
      return '';
    };

    if (Array.isArray(json.content)) {
      return json.content.map(renderNode).join('') || '<p></p>';
    }
  }

  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    const imgs = Array.from(doc.querySelectorAll('img'));
    imgs.forEach((img) => {
      const src = img.getAttribute('src') || '';
      const abs = toAbsoluteImageUrl(src);
      if (abs) img.setAttribute('src', abs);
      try {
        // Preserve saved width/height attributes if present so view reflects saved size.
        const widthAttr = img.getAttribute('width');
        const heightAttr = img.getAttribute('height');
        if (widthAttr) {
          img.style.width = widthAttr;
        }
        if (heightAttr) {
          img.style.height = heightAttr;
        }
        // Ensure images don't overflow container
        if (!widthAttr || !/%$/.test(String(widthAttr))) {
          img.style.maxWidth = '100%';
        }
      } catch (e) {
        // ignore
      }
    });

    return Array.from(doc.body.childNodes)
      .map((node) => (node.outerHTML ? node.outerHTML : node.textContent))
      .join('') || '<p></p>';
  } catch (err) {
    console.error('HTML 이미지 변환 실패:', err);
    return html || '<p></p>';
  }
};

// Custom font family and size extensions to match editor
const FontFamily = Extension.create({
  name: 'fontFamily',
  addGlobalAttributes() {
    return [
      {
        types: ['textStyle'],
        attributes: {
          fontFamily: {
            default: null,
            parseHTML: (element) => {
              const value = element.style.fontFamily || '';
              return value ? value.split(',')[0].replaceAll('"', '').replaceAll("'", '') : null;
            },
            renderHTML: (attributes) => {
              if (!attributes.fontFamily) return {};
              return { style: `font-family: ${attributes.fontFamily}` };
            },
          },
        },
      },
    ];
  },
});

const FontSize = Extension.create({
  name: 'fontSize',
  addGlobalAttributes() {
    return [
      {
        types: ['textStyle'],
        attributes: {
          fontSize: {
            default: null,
            parseHTML: (element) => element.style.fontSize?.replace('px', '') || null,
            renderHTML: (attributes) => {
              if (!attributes.fontSize) return {};
              return { style: `font-size: ${attributes.fontSize}px` };
            },
          },
        },
      },
    ];
  },
});

// Minimal ResizableImage extension (no node view) to preserve attrs when rendering HTML
const ResizableImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      width: {
        default: null,
        parseHTML: (element) => element.getAttribute('width') || element.style.width || null,
        renderHTML: (attributes) => {
          if (!attributes.width) return {};
          return {
            width: attributes.width,
            style: `width: ${attributes.width}; ${attributes.height ? `height: ${attributes.height};` : 'height: auto;'}`,
          };
        },
      },
      height: {
        default: null,
        parseHTML: (element) => element.getAttribute('height') || element.style.height || null,
        renderHTML: (attributes) => {
          if (!attributes.height) return {};
          return {
            height: attributes.height,
            style: `height: ${attributes.height}; ${attributes.width ? `width: ${attributes.width};` : ''}`,
          };
        },
      },
      align: {
        default: 'left',
        parseHTML: (element) => element.getAttribute('data-align') || 'left',
        renderHTML: (attributes) => {
          const align = String(attributes.align || 'left');
          if (align === 'center') return { 'data-align': 'center', style: 'display: block; margin-left: auto; margin-right: auto;' };
          if (align === 'right') return { 'data-align': 'right', style: 'display: block; margin-left: auto; margin-right: 0;' };
          return { 'data-align': 'left', style: 'display: block; margin-left: 0; margin-right: auto;' };
        },
      },
    };
  },
});

const PostHtmlView = ({ html = '' }) => {
  const containerRef = useRef(null);
  const safeHtml = transformHtmlImages(html);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    // If html is an object (tiptap JSON), use a temporary TipTap Editor to produce HTML with extensions
    if (html && typeof html === 'object' && Array.isArray(html.content)) {
      try {
        const editor = new Editor({
          extensions: [
            StarterKit.configure({
              heading: { levels: [1, 2, 3] },
            }),
            Underline,
            TextStyle,
            FontFamily,
            FontSize,
            Color,
            Highlight.configure({ multicolor: true }),
            ResizableImage.configure({
              inline: false,
              allowBase64: false,
            }),
            Link.configure({ openOnClick: false }),
            TextAlign.configure({ types: ['heading', 'paragraph'] }),
          ],
          editable: false,
          content: html,
        });

        const rendered = editor.getHTML();
        container.innerHTML = rendered;

        // Ensure saved width/height attributes applied as inline styles
        const imgs = Array.from(container.querySelectorAll('img'));
        imgs.forEach((img) => {
          try {
            const widthAttr = img.getAttribute('width');
            const heightAttr = img.getAttribute('height');
            if (widthAttr) img.style.width = widthAttr;
            if (heightAttr) img.style.height = heightAttr;
            if (!widthAttr || !/%$/.test(String(widthAttr))) img.style.maxWidth = '100%';
            // normalize src
            img.src = toAbsoluteImageUrl(img.getAttribute('src') || '');
          } catch (e) {}
        });

        // cleanup
        try {
          editor.destroy();
        } catch (e) {}

        console.log('PostHtmlView - rendered from JSON via TipTap');
        console.log('PostHtmlView - raw json:', html);
        return;
      } catch (err) {
        console.error('TipTap render error:', err);
        // fallback to previous behavior
      }
    }

    // Set innerHTML directly (we computed safeHtml already)
    container.innerHTML = safeHtml;

    // Post-processing: ensure images with data-src or lazy attributes are applied
    const imgs = Array.from(container.querySelectorAll('img'));
    imgs.forEach((img) => {
      try {
        const currentSrc = img.getAttribute('src') || '';
        const dataSrc = img.getAttribute('data-src') || img.getAttribute('dataSrc') || img.getAttribute('data-lazy') || img.getAttribute('data-lazy-src');
        if ((!currentSrc || currentSrc.trim() === '') && dataSrc) {
          const abs = toAbsoluteImageUrl(dataSrc);
          img.setAttribute('src', abs);
        }

        // If src is set but image naturalWidth is 0 after some delay, try resetting src to force load
        const ensureVisible = () => {
          try {
            img.style.maxWidth = '100%';
            img.style.height = 'auto';
            if (img.width) img.removeAttribute('width');
          } catch {}
        };

        ensureVisible();
      } catch (e) {
        // ignore per-image errors
      }
    });

    // debug logs
    try {
      console.log('PostHtmlView - container innerHTML set');
      console.log('PostHtmlView - raw html:', html);
      console.log('PostHtmlView - transformed html:', safeHtml);
    } catch (e) {}
  }, [html, safeHtml]);

  return <div ref={containerRef} className={styles.content} />;
};

export default PostHtmlView;
