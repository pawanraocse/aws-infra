/**
 * Cognito Pre Token Generation Lambda Trigger (V2)
 * 
 * This Lambda function is triggered before Cognito generates JWT tokens.
 * It injects the user's tenantId from custom attributes into the access token and ID token.
 * 
 * Event Version: V2_0 (required for access token customization)
 * Runtime: Node.js 20.x
 */

// Use ES module export syntax
export const handler = async (event) => {
    console.log('Pre Token Generation V2 Event:', JSON.stringify(event, null, 2));

    try {
        // Get user attributes from the event
        const userAttributes = event.request.userAttributes;

        // Extract the custom tenantId (default to 'default' if not found)
        const tenantId = userAttributes['custom:tenantId'] || 'default';

        console.log('Found tenantId:', tenantId);

        // Initialize the response object if it doesn't exist
        if (!event.response) {
            event.response = {};
        }

        // V2 Response Format - claimsAndScopeOverrideDetails should be an OBJECT
        event.response.claimsAndScopeOverrideDetails = {
            // Customize access token
            accessTokenGeneration: {
                claimsToAddOrOverride: {
                    'tenantId': tenantId,
                    'custom:tenantId': tenantId
                }
            },
            // Customize ID token (optional)
            idTokenGeneration: {
                claimsToAddOrOverride: {
                    'tenantId': tenantId
                }
            }
        };

        console.log('V2 Response:', JSON.stringify(event.response, null, 2));

        return event;

    } catch (error) {
        console.error('Error in Pre Token Generation V2:', error);
        // Return the event unchanged if there's an error
        // This prevents authentication failures due to Lambda errors
        return event;
    }
};
