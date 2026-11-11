import { APP_INITIALIZER } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app/app.routes';
import { authInterceptor } from './app/services/auth.interceptor';
import { AuthService } from './app/services/auth.service';

export function initializeAuthSessionFactory(authService: AuthService): () => void {
  return () => {
    authService.initializeSession();
  };
}

bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuthSessionFactory,
      deps: [AuthService],
      multi: true
    }
  ]
}).catch(err => console.error(err));
