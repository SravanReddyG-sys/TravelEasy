import { resolveImageUrl, DEFAULT_HOTEL_PLACEHOLDER } from './image-url.util';
import { environment } from '../../../environments/environment';

describe('image-url.util', () => {
  it('should return default fallback when url is null or undefined or empty', () => {
    expect(resolveImageUrl(null)).toBe(DEFAULT_HOTEL_PLACEHOLDER);
    expect(resolveImageUrl(undefined)).toBe(DEFAULT_HOTEL_PLACEHOLDER);
    expect(resolveImageUrl('')).toBe(DEFAULT_HOTEL_PLACEHOLDER);
    expect(resolveImageUrl('   ')).toBe(DEFAULT_HOTEL_PLACEHOLDER);
  });

  it('should preserve absolute HTTP and HTTPS URLs', () => {
    const httpUrl = 'http://example.com/images/hotel.jpg';
    const httpsUrl = 'https://images.unsplash.com/photo-12345?w=500';
    expect(resolveImageUrl(httpUrl)).toBe(httpUrl);
    expect(resolveImageUrl(httpsUrl)).toBe(httpsUrl);
  });

  it('should preserve data: and assets/ URLs', () => {
    const dataUrl = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA';
    const assetUrl = 'assets/images/custom-placeholder.png';
    expect(resolveImageUrl(dataUrl)).toBe(dataUrl);
    expect(resolveImageUrl(assetUrl)).toBe(assetUrl);
  });

  it('should resolve relative API image paths using environment base URL', () => {
    const relativePath = '/api/hotels/images/hotel_123.jpg';
    const base = (environment.apiGatewayUrl || '').replace(/\/+$/, '');
    expect(resolveImageUrl(relativePath)).toBe(`${base}/api/hotels/images/hotel_123.jpg`);
  });

  it('should ensure leading slash on relative paths', () => {
    const relativeNoSlash = 'api/hotels/images/hotel_456.jpg';
    const base = (environment.apiGatewayUrl || '').replace(/\/+$/, '');
    expect(resolveImageUrl(relativeNoSlash)).toBe(`${base}/api/hotels/images/hotel_456.jpg`);
  });
});
