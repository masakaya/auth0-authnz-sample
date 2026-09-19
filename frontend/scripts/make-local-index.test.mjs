import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import { domainFromEnvironment, localIndexHtml } from './make-local-index.mjs';

const indexHtml = readFileSync(new URL('../src/index.html', import.meta.url), 'utf8');

function policy(html) {
  return html.match(/http-equiv="Content-Security-Policy" content="([^"]+)"/)[1];
}

function directive(html, name) {
  return policy(html)
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name} `));
}

test('adds a custom login domain to connect-src and frame-src', () => {
  const html = localIndexHtml(indexHtml, 'login.example.test');

  assert.match(directive(html, 'connect-src'), / https:\/\/login\.example\.test( |$)/);
  assert.match(directive(html, 'frame-src'), / https:\/\/login\.example\.test( |$)/);
  assert.equal(directive(html, 'script-src'), "script-src 'self'");
});

test('leaves the policy unchanged for a default tenant domain', () => {
  assert.equal(localIndexHtml(indexHtml, 'example.eu.auth0.com'), indexHtml);
});

test('rejects a domain that carries a scheme, a path or policy syntax', () => {
  for (const domain of ['https://login.example.test', 'login.example.test/x', "x; script-src *", '']) {
    assert.throws(() => localIndexHtml(indexHtml, domain));
  }
});

test('fails loudly when index.html no longer has the source it rewrites', () => {
  assert.throws(() => localIndexHtml('<html></html>', 'login.example.test'));
});

test('reads the domain from the local environment file', () => {
  const example = readFileSync(
    new URL('../src/environments/environment.local.ts.example', import.meta.url),
    'utf8',
  );

  assert.equal(domainFromEnvironment(example), 'your-tenant.eu.auth0.com');
  assert.throws(() => domainFromEnvironment('export const environment = {};'));
});
