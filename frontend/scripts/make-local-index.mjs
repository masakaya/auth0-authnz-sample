// Generates src/index.local.html (git-ignored) from src/index.html for the `local` build
// configuration. The committed index.html only allows the identity provider's default
// domains in its content security policy; a custom login domain must not be committed, so
// it is taken from src/environments/environment.local.ts and written into the local copy.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_IDP_SOURCE = 'https://*.auth0.com';

export function localIndexHtml(indexHtml, domain) {
  if (!/^[a-z0-9.-]+$/i.test(domain)) {
    throw new Error(`auth0.domain must be a bare host name without a scheme, got "${domain}"`);
  }
  if (!indexHtml.includes(DEFAULT_IDP_SOURCE)) {
    throw new Error(`src/index.html no longer contains ${DEFAULT_IDP_SOURCE}; update this script`);
  }
  // Default tenant domains are already covered by the committed policy.
  if (domain.toLowerCase().endsWith('.auth0.com')) {
    return indexHtml;
  }
  return indexHtml.replaceAll(DEFAULT_IDP_SOURCE, `${DEFAULT_IDP_SOURCE} https://${domain}`);
}

export function domainFromEnvironment(source) {
  const match = source.match(/\bdomain:\s*['"]([^'"]+)['"]/);
  if (!match) {
    throw new Error('could not find auth0.domain in src/environments/environment.local.ts');
  }
  return match[1];
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  let environment;
  try {
    environment = readFileSync(resolve(root, 'src/environments/environment.local.ts'), 'utf8');
  } catch {
    console.error(
      'src/environments/environment.local.ts is missing. Copy environment.local.ts.example and fill it in.',
    );
    process.exit(1);
  }
  const domain = domainFromEnvironment(environment);
  const html = localIndexHtml(readFileSync(resolve(root, 'src/index.html'), 'utf8'), domain);
  writeFileSync(resolve(root, 'src/index.local.html'), html);
  console.log(`src/index.local.html written (identity provider domain: ${domain})`);
}
