import { environment } from '../../../environments/environment';

export const DEFAULT_HOTEL_PLACEHOLDER = 'assets/images/hotel-placeholder.svg';

/**
 * Resolves an image URL against the configured API Gateway / Environment:
 * - Returns a neutral local placeholder if url is empty or null.
 * - Preserves absolute URLs (http://, https://, data:) untouched.
 * - Preserves local asset URLs (assets/...) untouched.
 * - Resolves relative paths (starting with /) against the configured API Gateway base URL,
 *   avoiding duplicate '/api' segments.
 */
export function resolveImageUrl(url?: string | null, fallback: string = DEFAULT_HOTEL_PLACEHOLDER): string {
  if (!url || typeof url !== 'string') {
    return fallback;
  }
  const trimmed = url.trim();
  if (!trimmed) {
    return fallback;
  }
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://') || trimmed.startsWith('data:')) {
    return trimmed;
  }
  if (trimmed.startsWith('assets/')) {
    return trimmed;
  }

  const base = (environment.apiGatewayUrl || '').replace(/\/+$/, '');
  const path = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;

  return `${base}${path}`;
}
