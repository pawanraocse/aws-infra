export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080', // Gateway URL
  cognito: {
    userPoolId: 'us-east-1_JTWyGznRm',
    userPoolWebClientId: '5ipcdulrm15t1laniekdk3bmm0',  // SPA client (no secret)
    region: 'us-east-1'
  }
};
