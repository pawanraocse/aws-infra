export const environment = {
    production: true,
    apiUrl: 'http://localhost:8080', // Gateway URL (update for production)
    cognito: {
        userPoolId: 'us-east-1_JTWyGznRm',
        userPoolWebClientId: '5ipcdulrm15t1laniekdk3bmm0',  // SPA client (no secret)
        region: 'us-east-1'
    }
};
