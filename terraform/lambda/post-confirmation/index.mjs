/**
 * Cognito Post-Confirmation Lambda Trigger
 * 
 * Automatically provisions a tenant in the platform-service when a user
 * confirms their account signup.
 * 
 * Event: PostConfirmation_ConfirmSignUp
 * Trigger: After user confirms email/phone
 */

export const handler = async (event) => {
    console.log('Post-confirmation event:', JSON.stringify(event, null, 2));

    const { userAttributes, request } = event;

    // Extract tenant information
    const tenantId = userAttributes['custom:tenantId'] || 'default';
    const email = userAttributes.email;
    const username = userAttributes['cognito:username'];

    console.log(`Provisioning tenant for user: ${email}, tenantId: ${tenantId}`);

    // Platform service endpoint
    const platformServiceUrl = process.env.PLATFORM_SERVICE_URL || 'http://platform-service:8083/platform';

    try {
        // Call platform-service to provision tenant
        const response = await fetch(`${platformServiceUrl}/api/tenants`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                id: tenantId,
                name: `Tenant ${tenantId}`,
                storageMode: 'DATABASE',
                slaTier: 'STANDARD'
            })
        });

        const responseText = await response.text();

        if (response.ok) {
            console.log(`✅ Tenant provisioned successfully: ${tenantId}`, responseText);
        } else if (response.status === 409) {
            console.log(`ℹ️  Tenant already exists: ${tenantId}`);
            // This is fine - tenant may have been created previously
        } else {
            console.error(`❌ Failed to provision tenant: ${tenantId}, status: ${response.status}, response: ${responseText}`);
            // Don't fail the signup process - tenant can be provisioned manually later
            // Just log the error
        }
    } catch (error) {
        console.error(`❌ Error calling platform-service: ${error.message}`);
        // Don't fail signup - allow user to continue
        // Tenant can be provisioned through retry mechanism later
    }

    // Always return the event to allow signup to continue
    return event;
};
