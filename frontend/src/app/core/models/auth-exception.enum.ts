/**
 * Types of authentication exceptions that can occur.
 * Used for categorizing and handling auth errors in a consistent way.
 */
export enum AuthExceptionType {
    // User-initiated or expected states
    USER_NOT_CONFIRMED = 'UserNotConfirmedException',
    NEW_PASSWORD_REQUIRED = 'NewPasswordChallenge',

    // Credential errors
    INVALID_CREDENTIALS = 'NotAuthorizedException',
    USER_NOT_FOUND = 'UserNotFoundException',

    // Account state errors
    USER_DISABLED = 'UserDisabledException',
    PASSWORD_RESET_REQUIRED = 'PasswordResetRequiredException',

    // Validation errors
    INVALID_PARAMETER = 'InvalidParameterException',
    INVALID_PASSWORD = 'InvalidPasswordException',

    // Rate limiting
    TOO_MANY_ATTEMPTS = 'TooManyRequestsException',
    LIMIT_EXCEEDED = 'LimitExceededException',

    // Network/Service errors
    NETWORK_ERROR = 'NetworkError',
    SERVICE_UNAVAILABLE = 'ServiceUnavailableException',

    // Multi-tenant specific
    NO_TENANTS_FOUND = 'NoTenantsFoundException',
    SSO_NOT_CONFIGURED = 'SsoNotConfiguredException',

    // Generic fallback
    UNKNOWN = 'UnknownException'
}

/**
 * Map Cognito/Amplify error names to our exception types.
 * This provides a consistent interface for handling auth errors.
 */
export function classifyAuthError(error: unknown): AuthExceptionType {
    if (!error || typeof error !== 'object') {
        return AuthExceptionType.UNKNOWN;
    }

    const e = error as { name?: string; code?: string; message?: string };
    const name = e.name || e.code || '';

    // Map known error names
    const mappings: Record<string, AuthExceptionType> = {
        'UserNotConfirmedException': AuthExceptionType.USER_NOT_CONFIRMED,
        'NewPasswordChallenge': AuthExceptionType.NEW_PASSWORD_REQUIRED,
        'NotAuthorizedException': AuthExceptionType.INVALID_CREDENTIALS,
        'UserNotFoundException': AuthExceptionType.USER_NOT_FOUND,
        'UserDisabledException': AuthExceptionType.USER_DISABLED,
        'PasswordResetRequiredException': AuthExceptionType.PASSWORD_RESET_REQUIRED,
        'InvalidParameterException': AuthExceptionType.INVALID_PARAMETER,
        'InvalidPasswordException': AuthExceptionType.INVALID_PASSWORD,
        'TooManyRequestsException': AuthExceptionType.TOO_MANY_ATTEMPTS,
        'LimitExceededException': AuthExceptionType.LIMIT_EXCEEDED,
        'NetworkError': AuthExceptionType.NETWORK_ERROR,
        'ServiceUnavailableException': AuthExceptionType.SERVICE_UNAVAILABLE
    };

    return mappings[name] || AuthExceptionType.UNKNOWN;
}

/**
 * Get user-friendly error message for an auth exception type.
 */
export function getAuthErrorMessage(type: AuthExceptionType): string {
    const messages: Record<AuthExceptionType, string> = {
        [AuthExceptionType.USER_NOT_CONFIRMED]: 'Please verify your email address before logging in.',
        [AuthExceptionType.NEW_PASSWORD_REQUIRED]: 'Please set a new password to continue.',
        [AuthExceptionType.INVALID_CREDENTIALS]: 'Invalid email or password. Please try again.',
        [AuthExceptionType.USER_NOT_FOUND]: 'No account found with this email address.',
        [AuthExceptionType.USER_DISABLED]: 'This account has been disabled. Please contact support.',
        [AuthExceptionType.PASSWORD_RESET_REQUIRED]: 'Password reset required. Please check your email.',
        [AuthExceptionType.INVALID_PARAMETER]: 'Invalid input. Please check your entries.',
        [AuthExceptionType.INVALID_PASSWORD]: 'Password does not meet requirements.',
        [AuthExceptionType.TOO_MANY_ATTEMPTS]: 'Too many attempts. Please wait and try again.',
        [AuthExceptionType.LIMIT_EXCEEDED]: 'Request limit exceeded. Please wait and try again.',
        [AuthExceptionType.NETWORK_ERROR]: 'Network error. Please check your connection.',
        [AuthExceptionType.SERVICE_UNAVAILABLE]: 'Service temporarily unavailable. Please try again later.',
        [AuthExceptionType.NO_TENANTS_FOUND]: 'No workspaces found for this email. Please sign up first.',
        [AuthExceptionType.SSO_NOT_CONFIGURED]: 'SSO is not yet configured for this organization.',
        [AuthExceptionType.UNKNOWN]: 'An unexpected error occurred. Please try again.'
    };

    return messages[type];
}

/**
 * Determine if an auth error is user-recoverable (vs. requires support).
 */
export function isRecoverableAuthError(type: AuthExceptionType): boolean {
    const recoverableTypes = new Set([
        AuthExceptionType.USER_NOT_CONFIRMED,
        AuthExceptionType.NEW_PASSWORD_REQUIRED,
        AuthExceptionType.INVALID_CREDENTIALS,
        AuthExceptionType.INVALID_PASSWORD,
        AuthExceptionType.TOO_MANY_ATTEMPTS,
        AuthExceptionType.NETWORK_ERROR,
        AuthExceptionType.NO_TENANTS_FOUND
    ]);

    return recoverableTypes.has(type);
}
