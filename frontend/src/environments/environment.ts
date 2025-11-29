export const environment = {
    production: true,
    apiUrl: 'http://localhost:8080', // Gateway URL (update for production)
    cognito: {
        userPoolId: 'us-east-1_jjRFRnxGA',
        userPoolWebClientId: '4r5pkt8drle8e5ogrpl7rpe45p',  // SPA client (no secret)
        region: 'us-east-1'
    }
};
