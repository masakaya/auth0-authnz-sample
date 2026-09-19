/**
 * Shape shared by `environment.ts` (committed placeholders) and `environment.local.ts`
 * (never committed). Keeping it in its own file lets the local override be type checked,
 * because the `local` build configuration replaces `environment.ts` as a whole.
 */
export interface EnvironmentConfig {
  readonly production: boolean;

  /** Origin of the legacy API stand-in. Only requests to it receive a bearer token. */
  readonly apiOrigin: string;

  readonly auth0: {
    readonly domain: string;
    readonly clientId: string;
    /** API identifier requested as the access token audience. */
    readonly audience: string;
  };
}
