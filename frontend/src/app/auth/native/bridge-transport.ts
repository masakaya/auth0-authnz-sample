import { InjectionToken } from '@angular/core';

/**
 * The OS specific half of the native bridge. This is the only place allowed to touch
 * `window`; the message handling above it is shared by every platform.
 */
export interface BridgeTransport {
  /** Short identifier of the transport, for diagnostics and the status display. */
  readonly name: string;

  /** True when the host application injected the bridge object into this page. */
  isAvailable(): boolean;

  /** Starts delivering native to web payloads (raw JSON strings) to `onMessage`. */
  connect(onMessage: (raw: unknown) => void): void;

  disconnect(): void;

  /** Sends a raw JSON string web to native. */
  postMessage(raw: string): void;
}

export const BRIDGE_TRANSPORT = new InjectionToken<BridgeTransport>('BRIDGE_TRANSPORT');
