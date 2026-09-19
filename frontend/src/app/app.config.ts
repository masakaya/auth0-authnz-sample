import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { provideTokenSource } from './auth/token-source-providers';
import { API_ORIGIN } from './core/api-config';
import { apiAuthInterceptor } from './http/api-auth-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([apiAuthInterceptor])),
    { provide: API_ORIGIN, useValue: environment.apiOrigin },
    // Decides between the browser flow and the native bridge before anything else runs.
    provideTokenSource(),
  ],
};
