import { Routes } from '@angular/router';
import { Home } from './home/home';

export const routes: Routes = [
  // The identity provider redirects back to the application root, so this route also
  // receives the authorization code callback.
  { path: '', component: Home },
  { path: '**', redirectTo: '' },
];
