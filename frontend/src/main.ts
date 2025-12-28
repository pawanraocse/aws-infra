import {bootstrapApplication} from '@angular/platform-browser';
import {appConfig} from './app/app.config';
import {App} from './app/app';
import {Amplify} from 'aws-amplify';
import {environment} from './environments/environment';

/**
 * Fetch Cognito config from gateway and configure Amplify.
 * Falls back to environment.ts if gateway is unavailable.
 */
async function initializeApp(): Promise<void> {
  let userPoolId = environment.cognito.userPoolId;
  let userPoolClientId = environment.cognito.userPoolWebClientId;

  try {
    const response = await fetch(`${environment.apiUrl}/api/config/cognito`);
    if (response.ok) {
      const config = await response.json();
      if (config.userPoolId && config.clientId) {
        userPoolId = config.userPoolId;
        userPoolClientId = config.clientId;
        console.log('[App] Loaded Cognito config from gateway');
      }
    }
  } catch (error) {
    console.warn('[App] Failed to fetch config from gateway, using defaults:', error);
  }

  // Configure Amplify with the loaded config
  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId,
        userPoolClientId,
      }
    }
  });

  // Bootstrap the Angular application
  bootstrapApplication(App, appConfig)
    .catch((err) => console.error(err));
}

initializeApp();

