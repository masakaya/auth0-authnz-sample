/**
 * Post-login Action: copies the customer's brand membership and customer role from
 * app_metadata into namespaced custom claims on the access token.
 *
 * It only reads app_metadata (no external calls) and fails closed: malformed or unknown
 * values are dropped instead of being passed on to the API.
 *
 * Secrets:
 *   CLAIM_NAMESPACE  e.g. https://authnz.example.com (no trailing slash)
 */

const DEFAULT_NAMESPACE = 'https://authnz.example.com';
const BRAND_ID_PATTERN = /^brand-[a-z0-9]+(-[a-z0-9]+)*$/;
const CUSTOMER_ROLES = ['role1', 'role2'];

function resolveBrands(appMetadata) {
  const brands = appMetadata && appMetadata.brands;
  if (!Array.isArray(brands)) {
    return [];
  }
  const valid = brands.filter((brand) => typeof brand === 'string' && BRAND_ID_PATTERN.test(brand));
  return [...new Set(valid)];
}

function resolveCustomerRole(appMetadata) {
  const role = appMetadata && appMetadata.customer_role;
  return CUSTOMER_ROLES.includes(role) ? role : null;
}

exports.onExecutePostLogin = async (event, api) => {
  const configured = event.secrets && event.secrets.CLAIM_NAMESPACE;
  const namespace = (configured || DEFAULT_NAMESPACE).replace(/\/+$/, '');
  const appMetadata = event.user && event.user.app_metadata;

  // Always set the brands claim, so that "no brands" is an explicit empty list on the token.
  api.accessToken.setCustomClaim(`${namespace}/brands`, resolveBrands(appMetadata));

  const customerRole = resolveCustomerRole(appMetadata);
  if (customerRole !== null) {
    api.accessToken.setCustomClaim(`${namespace}/customer_role`, customerRole);
  }
};

exports.resolveBrands = resolveBrands;
exports.resolveCustomerRole = resolveCustomerRole;
