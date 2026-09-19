/**
 * Message contract shared with the native hosts (bridge specification, version 1).
 * Only the transport is OS specific; the messages below are identical everywhere and
 * are always exchanged as JSON strings.
 */
export const BRIDGE_PROTOCOL_VERSION = 1;

/** Failure codes the host may answer a `getToken` request with. */
export const BRIDGE_ERROR_CODES = [
  'not_logged_in',
  'refresh_failed',
  'user_cancelled',
  'locked',
  'internal_error',
] as const;

export type BridgeErrorCode = (typeof BRIDGE_ERROR_CODES)[number];

/** Web to native: ask for an access token. */
export interface GetTokenMessage {
  readonly version: number;
  readonly type: 'getToken';
  readonly requestId: string;
  readonly forceRefresh: boolean;
}

/** Native to web: successful answer to `getToken`. */
export interface TokenResultMessage {
  readonly version: number;
  readonly type: 'tokenResult';
  readonly requestId: string;
  readonly accessToken: string;
  /** UNIX time in milliseconds. */
  readonly expiresAt: number;
}

/** Native to web: failed answer to `getToken`. */
export interface ErrorMessage {
  readonly version: number;
  readonly type: 'error';
  readonly requestId: string;
  readonly code: BridgeErrorCode;
  /** Diagnostic only; never shown to the user. */
  readonly message?: string;
}

/** Native to web: the host signed the user out. Carries no `requestId`. */
export interface LogoutMessage {
  readonly version: number;
  readonly type: 'logout';
}

export type NativeToWebMessage = TokenResultMessage | ErrorMessage | LogoutMessage;

/**
 * Turns a raw payload into a known message, or `null` when it must be ignored.
 * Malformed JSON, unknown protocol versions, unknown types and incomplete payloads all
 * yield `null` instead of throwing, as required by the specification.
 */
export function parseNativeMessage(raw: unknown): NativeToWebMessage | null {
  if (typeof raw !== 'string') {
    return null;
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }

  if (!isRecord(parsed) || parsed['version'] !== BRIDGE_PROTOCOL_VERSION) {
    return null;
  }

  switch (parsed['type']) {
    case 'tokenResult':
      return parseTokenResult(parsed);
    case 'error':
      return parseError(parsed);
    case 'logout':
      return { version: BRIDGE_PROTOCOL_VERSION, type: 'logout' };
    default:
      return null;
  }
}

function parseTokenResult(parsed: Record<string, unknown>): TokenResultMessage | null {
  const requestId = parsed['requestId'];
  const accessToken = parsed['accessToken'];
  const expiresAt = parsed['expiresAt'];

  if (!isNonEmptyString(requestId) || !isNonEmptyString(accessToken) || !isFiniteNumber(expiresAt)) {
    return null;
  }

  return { version: BRIDGE_PROTOCOL_VERSION, type: 'tokenResult', requestId, accessToken, expiresAt };
}

function parseError(parsed: Record<string, unknown>): ErrorMessage | null {
  const requestId = parsed['requestId'];
  const code = parsed['code'];
  const message = parsed['message'];

  if (!isNonEmptyString(requestId) || !isErrorCode(code)) {
    return null;
  }

  return {
    version: BRIDGE_PROTOCOL_VERSION,
    type: 'error',
    requestId,
    code,
    message: typeof message === 'string' ? message : undefined,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isErrorCode(value: unknown): value is BridgeErrorCode {
  return typeof value === 'string' && (BRIDGE_ERROR_CODES as readonly string[]).includes(value);
}
