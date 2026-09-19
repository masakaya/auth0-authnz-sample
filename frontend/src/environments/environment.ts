import { EnvironmentConfig } from './environment-config';

/**
 * Committed defaults. The identity provider values below are placeholders on purpose.
 * Put real tenant values in `environment.local.ts` (git-ignored; copy
 * `environment.local.ts.example`) and run `npm run start:local`, which swaps this file
 * out through the `fileReplacements` of the `local` build configuration.
 */
export const environment: EnvironmentConfig = {
  production: false,
  apiOrigin: 'http://localhost:8080',
  auth0: {
    domain: 'your-tenant.example.auth0.com',
    clientId: 'REPLACE_WITH_YOUR_CLIENT_ID',
    audience: 'https://api.example.test/legacy',
  },
};
