/** One endpoint of the legacy API stand-in, as offered on the screen. */
export interface ApiEndpoint {
  readonly id: string;
  readonly method: 'GET';
  readonly path: string;
  /** What the API requires for this path, shown next to the button. */
  readonly requires: string;
}

export const API_ENDPOINTS: readonly ApiEndpoint[] = [
  { id: 'public', method: 'GET', path: '/api/public', requires: 'nothing, Guest allowed' },
  { id: 'private', method: 'GET', path: '/api/private', requires: 'any signed-in user' },
  { id: 'brand-a-offers', method: 'GET', path: '/api/brand-a/offers', requires: 'ROLE_BRAND_A' },
  { id: 'brand-b-offers', method: 'GET', path: '/api/brand-b/offers', requires: 'ROLE_BRAND_B' },
  {
    id: 'brand-a-role2-only',
    method: 'GET',
    path: '/api/brand-a/role2-only',
    requires: 'ROLE_BRAND_A and ROLE_ROLE2',
  },
  { id: 'admin', method: 'GET', path: '/api/admin', requires: 'read:admin' },
];
