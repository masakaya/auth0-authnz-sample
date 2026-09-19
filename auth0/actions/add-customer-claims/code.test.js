const test = require('node:test');
const assert = require('node:assert/strict');

const { onExecutePostLogin } = require('./code.js');

const NAMESPACE = 'https://authnz.example.com';

async function run(appMetadata, secrets = { CLAIM_NAMESPACE: NAMESPACE }) {
  const claims = {};
  const api = {
    accessToken: {
      setCustomClaim: (name, value) => {
        claims[name] = value;
      },
    },
  };
  await onExecutePostLogin({ user: { app_metadata: appMetadata }, secrets }, api);
  return claims;
}

test('sets brands and customer_role from app_metadata', async () => {
  const claims = await run({ brands: ['brand-a', 'brand-b'], customer_role: 'role2' });

  assert.deepEqual(claims, {
    [`${NAMESPACE}/brands`]: ['brand-a', 'brand-b'],
    [`${NAMESPACE}/customer_role`]: 'role2',
  });
});

test('sets an empty brands claim and no customer_role when app_metadata is missing', async () => {
  for (const appMetadata of [undefined, null, {}]) {
    const claims = await run(appMetadata);

    assert.deepEqual(claims, { [`${NAMESPACE}/brands`]: [] });
  }
});

test('drops malformed, non-string and duplicate brand ids', async () => {
  const claims = await run({ brands: ['brand-a', 'role2', 'guest', 'BRAND-B', 42, null, 'brand-a', 'brand-'] });

  assert.deepEqual(claims[`${NAMESPACE}/brands`], ['brand-a']);
});

test('treats a non-array brands value as no brands', async () => {
  for (const brands of ['brand-a', { 0: 'brand-a' }, 1]) {
    const claims = await run({ brands });

    assert.deepEqual(claims[`${NAMESPACE}/brands`], []);
  }
});

test('omits customer_role when the value is unknown or wrongly typed', async () => {
  for (const role of ['admin', 'ROLE2', '', 2, ['role1'], null]) {
    const claims = await run({ brands: [], customer_role: role });

    assert.equal(`${NAMESPACE}/customer_role` in claims, false);
  }
});

test('falls back to the default namespace and tolerates a trailing slash', async () => {
  const withoutSecret = await run({ brands: ['brand-a'] }, {});
  const withTrailingSlash = await run({ brands: ['brand-a'] }, { CLAIM_NAMESPACE: `${NAMESPACE}/` });

  assert.deepEqual(withoutSecret, { [`${NAMESPACE}/brands`]: ['brand-a'] });
  assert.deepEqual(withTrailingSlash, { [`${NAMESPACE}/brands`]: ['brand-a'] });
});

test('only touches the access token', async () => {
  const api = {
    accessToken: { setCustomClaim: () => {} },
    idToken: {
      setCustomClaim: () => {
        throw new Error('the id token must not be modified');
      },
    },
  };

  await onExecutePostLogin({ user: { app_metadata: { brands: ['brand-a'] } }, secrets: {} }, api);
});
