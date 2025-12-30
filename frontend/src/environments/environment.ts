export const environment = {
    production: false, // Set to true in environment.prod.ts
    apiUrl: 'http://localhost:8080', // Gateway URL (update for production)
    cognito: {
        userPoolId: 'us-east-1_JTWyGznRm',
        userPoolWebClientId: '5ipcdulrm15t1laniekdk3bmm0',  // SPA client (no secret)
        domain: 'cloud-infra-dev-9dosle0q.auth.us-east-1.amazoncognito.com',
        region: 'us-east-1'
    }
};
