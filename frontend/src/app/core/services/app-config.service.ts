import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';

/**
 * Configuration interface for Cognito settings
 */
export interface CognitoConfig {
    userPoolId: string;
    clientId: string;
    region: string;
}

/**
 * Service to load application configuration from the gateway.
 * Fetches Cognito config from /api/config/cognito endpoint at startup.
 * Falls back to environment.ts if the endpoint is unavailable.
 */
@Injectable({
    providedIn: 'root'
})
export class AppConfigService {
    private cognitoConfig: CognitoConfig | null = null;
    private configLoaded = false;

    /**
     * Load configuration from the gateway.
     * Called during app initialization.
     */
    async loadConfig(): Promise<void> {
        try {
            const response = await fetch(`${environment.apiUrl}/api/config/cognito`);
            if (response.ok) {
                const config = await response.json();
                this.cognitoConfig = {
                    userPoolId: config.userPoolId,
                    clientId: config.clientId,
                    region: config.region || 'us-east-1'
                };
                console.log('[AppConfig] Loaded Cognito config from gateway');
                this.configLoaded = true;
            } else {
                console.warn('[AppConfig] Failed to fetch config from gateway, using environment defaults');
                this.useEnvironmentDefaults();
            }
        } catch (error) {
            console.warn('[AppConfig] Error fetching config:', error);
            this.useEnvironmentDefaults();
        }
    }

    private useEnvironmentDefaults(): void {
        this.cognitoConfig = {
            userPoolId: environment.cognito.userPoolId,
            clientId: environment.cognito.userPoolWebClientId,
            region: environment.cognito.region
        };
        this.configLoaded = true;
    }

    /**
     * Get the Cognito configuration.
     * Must be called after loadConfig() has completed.
     */
    getCognitoConfig(): CognitoConfig {
        if (!this.cognitoConfig) {
            // Fallback if called before loadConfig
            this.useEnvironmentDefaults();
        }
        return this.cognitoConfig!;
    }

    /**
     * Check if config has been loaded
     */
    isConfigLoaded(): boolean {
        return this.configLoaded;
    }
}
