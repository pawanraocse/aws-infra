export const environment = {
    production: true,
    apiUrl: 'http://localhost:8080', // Gateway URL (update for production)
    cognito: {
        userPoolId: 'us-east-1_mxcYdW1PQ',
        userPoolWebClientId: '6u2rfgheeo6jjmnodoi67fjp9f',  // SPA client (no secret)
        region: 'us-east-1'
    }
};
