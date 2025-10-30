import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { StartupService } from './app/core/startup.service';

bootstrapApplication(App, appConfig)
  .then(() => {
    // Run startup auth check
    const startup = new StartupService();
    return startup.init();
  })
  .catch((err) => console.error(err));
