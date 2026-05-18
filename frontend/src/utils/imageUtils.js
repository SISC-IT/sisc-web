const DEFAULT_IMAGE_ORIGIN = 'https://api.sisc.kr';

export const isDataImageUrl = (value = '') => /^data:image\//i.test(String(value || '').trim());

export const resolveImageOrigin = () => {
  const configuredApiUrl = String(import.meta.env.VITE_API_URL || '').trim();
  if (!configuredApiUrl) return DEFAULT_IMAGE_ORIGIN;

  try {
    return new URL(configuredApiUrl).origin;
  } catch {
    return DEFAULT_IMAGE_ORIGIN;
  }
};

export const toAbsoluteImageUrl = (value = '') => {
  const src = String(value || '').trim();
  if (!src) return '';
  if (/^data:/i.test(src) || /^blob:/i.test(src)) {
    return src;
  }

  let normalizedPath = src;
  const API_IMAGE_ORIGIN = resolveImageOrigin();

  if (/^(https?:)?\/\//i.test(src)) {
    try {
      normalizedPath = new URL(src, API_IMAGE_ORIGIN).pathname || src;
    } catch {
      normalizedPath = src;
    }
  }

  if (!normalizedPath.startsWith('/')) {
    normalizedPath = `/${normalizedPath.replace(/^\/+/, '')}`;
  }

  const uploadsImagesIndex = normalizedPath.indexOf('/uploads/images/');
  if (uploadsImagesIndex >= 0) {
    normalizedPath = normalizedPath.slice(uploadsImagesIndex);
  }

  return `${API_IMAGE_ORIGIN}${normalizedPath}`;
};

export const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'heic', 'heif'];

export const isImageFile = (file) => {
  if (!file) return false;
  const mimeType = String(file.type || '').toLowerCase();
  if (mimeType.startsWith('image/')) return true;
  const filename = String(file.name || '').toLowerCase();
  const extension = filename.includes('.') ? filename.split('.').pop() : '';
  return IMAGE_EXTENSIONS.includes(extension);
};

export const dataUrlToFile = async (dataUrl, filename = 'pasted-image.png') => {
  const response = await fetch(dataUrl);
  const blob = await response.blob();
  const extension = blob.type?.split('/')?.[1] || 'png';
  const normalizedName = filename.includes('.') ? filename : `${filename}.${extension}`;
  return new File([blob], normalizedName, { type: blob.type || 'image/png' });
};

export const blobToFile = (blob, filename = 'pasted-image.png') => {
  const extension = blob.type?.split('/')?.[1] || 'png';
  const normalizedName = filename.includes('.') ? filename : `${filename}.${extension}`;
  return new File([blob], normalizedName, { type: blob.type || 'image/png' });
};

export const remoteUrlToFile = async (url, filename = 'pasted-image.png') => {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Failed to fetch image: ${response.status}`);
  const blob = await response.blob();
  return blobToFile(blob, filename);
};

export const extractImageSrcsFromHtml = (html = '') => {
  if (!html) return [];
  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    return Array.from(doc.querySelectorAll('img'))
      .map((img) => String(img.getAttribute('src') || '').trim())
      .filter(Boolean);
  } catch {
    return [];
  }
};
