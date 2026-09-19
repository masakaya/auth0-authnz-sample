import { InjectionToken } from '@angular/core';

/**
 * Origin of the legacy API stand-in. Requests to any other origin never carry a
 * bearer token, so the token cannot leak to third parties.
 */
export const API_ORIGIN = new InjectionToken<string>('API_ORIGIN');
