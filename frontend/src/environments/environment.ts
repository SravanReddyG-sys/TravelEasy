export const environment = {
  production: false,
  apiGatewayUrl: typeof window !== 'undefined' && window.location.port === '4200' ? 'http://localhost:8080' : ''
};
