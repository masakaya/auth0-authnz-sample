import { BridgeTransport } from './bridge-transport';

/** Object name every native host must use when injecting the bridge. */
export const NATIVE_BRIDGE_OBJECT_NAME = 'NativeAuthBridge';

/** Shape of the object injected by `WebViewCompat.addWebMessageListener`. */
interface AndroidBridgeObject {
  postMessage(message: string): void;
  onmessage?: ((event: { data?: unknown }) => void) | null;
}

/**
 * Android transport: the host injects an object exposing `postMessage` for web to native
 * and an assignable `onmessage` for native to web, where `event.data` is a JSON string.
 * The iOS (`webkit.messageHandlers`) and Windows (`chrome.webview`) transports will be
 * separate implementations of {@link BridgeTransport}; nothing else has to change.
 */
export class AndroidBridgeTransport implements BridgeTransport {
  readonly name = 'android';

  constructor(private readonly host: unknown = typeof window === 'undefined' ? undefined : window) {}

  isAvailable(): boolean {
    return this.bridge() !== null;
  }

  connect(onMessage: (raw: unknown) => void): void {
    const bridge = this.bridge();
    if (!bridge) {
      return;
    }
    bridge.onmessage = (event) => onMessage(event?.data);
  }

  disconnect(): void {
    const bridge = this.bridge();
    if (bridge) {
      bridge.onmessage = null;
    }
  }

  postMessage(raw: string): void {
    this.bridge()?.postMessage(raw);
  }

  private bridge(): AndroidBridgeObject | null {
    if (typeof this.host !== 'object' || this.host === null) {
      return null;
    }
    const candidate = (this.host as Record<string, unknown>)[NATIVE_BRIDGE_OBJECT_NAME];
    if (typeof candidate !== 'object' || candidate === null) {
      return null;
    }
    const bridge = candidate as AndroidBridgeObject;
    return typeof bridge.postMessage === 'function' ? bridge : null;
  }
}
