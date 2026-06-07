import { 
  ApplicationConfig, 
  APP_INITIALIZER, 
  importProvidersFrom, 
  inject, 
  provideAppInitializer, 
  provideBrowserGlobalErrorListeners 
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { MatSnackBarModule } from '@angular/material/snack-bar';

import { routes } from './app.routes';
import { errorInterceptor } from './core/auth/error-interceptor';
import { UserService } from './core/services/user.service';
import { ConfigService } from './core/services/config.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([errorInterceptor])),
    provideAnimations(),
    importProvidersFrom(MatSnackBarModule),
        provideAppInitializer(() => {
      const userService = inject(UserService);
      return userService.loadUserProfile();
    }),
    {
      provide: APP_INITIALIZER,
      useFactory: (config: ConfigService) => () => config.load(),
      deps: [ConfigService],
      multi: true
    }
  ]
};
