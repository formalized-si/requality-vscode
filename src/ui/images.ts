import * as fs from 'fs';
import * as path from 'path';

/** The image files a webview shows as data URIs when they are not in its local resource roots. */
const IMAGE_TYPES: Record<string, string> = {
  '.png': 'image/png',
  '.gif': 'image/gif',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.bmp': 'image/bmp',
  '.ico': 'image/x-icon',
};

/** Images larger than this stay URIs (icons and small pictures are much smaller). */
const MAX_DATA_URI_BYTES = 512 * 1024;

const dataUris = new Map<string, { mtime: number; uri: string }>();

function isUnder(file: string, root: string): boolean {
  const relative = path.relative(root, file);
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
}

/**
 * The URL of an image file in a webview: a webview URI when the file is in one of the local resource roots of the
 * webview, else a data URI of its contents (the images of node types that a catalog keeps in its folder, for example).
 */
export function imageUrl(file: string, roots: string[], webviewUri: (file: string) => string): string {
  if (roots.some(r => isUnder(file, r))) {
    return webviewUri(file);
  }
  const type = IMAGE_TYPES[path.extname(file).toLowerCase()];
  try {
    const stat = fs.statSync(file);
    if (!type || !stat.isFile() || stat.size > MAX_DATA_URI_BYTES) {
      return webviewUri(file);
    }
    const cached = dataUris.get(file);
    if (cached && cached.mtime === stat.mtimeMs) {
      return cached.uri;
    }
    const uri = `data:${type};base64,${fs.readFileSync(file).toString('base64')}`;
    dataUris.set(file, { mtime: stat.mtimeMs, uri });
    return uri;
  } catch {
    return webviewUri(file);
  }
}
