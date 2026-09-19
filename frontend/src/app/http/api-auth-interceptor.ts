import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, Observable, switchMap, throwError } from 'rxjs';
import { TOKEN_SOURCE } from '../auth/token-source';
import { API_ORIGIN } from '../core/api-config';

/**
 * Attaches the access token to API requests.
 *
 * Rules:
 * - only requests to the API origin are touched, never any other origin;
 * - without a token the request still goes out, as a Guest;
 * - a 401 triggers exactly one forced refresh and one retry, and if that also fails the
 *   request is retried once without a token. At most three requests leave the browser.
 */
export const apiAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const apiOrigin = inject(API_ORIGIN);
  const tokenSource = inject(TOKEN_SOURCE);

  if (!targetsOrigin(request.url, apiOrigin)) {
    return next(request);
  }

  return from(tokenSource.getAccessToken()).pipe(
    switchMap((token) =>
      attempt(request, next, token).pipe(
        catchError((error: unknown) => {
          // A Guest request carries no token, so there is nothing to refresh.
          if (!isUnauthorized(error) || token === null) {
            return throwError(() => error);
          }

          return from(tokenSource.getAccessToken({ forceRefresh: true })).pipe(
            switchMap((refreshed) => {
              if (refreshed === null) {
                // No token obtainable: fall back to Guest, and stop there.
                return attempt(request, next, null);
              }

              return attempt(request, next, refreshed).pipe(
                catchError((retryError: unknown) =>
                  isUnauthorized(retryError)
                    ? attempt(request, next, null)
                    : throwError(() => retryError),
                ),
              );
            }),
          );
        }),
      ),
    ),
  );
};

function attempt(
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
  token: string | null,
): Observable<HttpEvent<unknown>> {
  const headers =
    token === null
      ? request.headers.delete('Authorization')
      : request.headers.set('Authorization', `Bearer ${token}`);

  return next(request.clone({ headers }));
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}

function targetsOrigin(url: string, apiOrigin: string): boolean {
  try {
    const base = typeof document === 'undefined' ? undefined : document.baseURI;
    return new URL(url, base).origin === new URL(apiOrigin).origin;
  } catch {
    return false;
  }
}
