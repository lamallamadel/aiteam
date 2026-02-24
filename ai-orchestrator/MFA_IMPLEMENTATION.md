# MFA Implementation

## Overview
TOTP-based Multi-Factor Authentication has been implemented using the `dev.samstevens.totp` library.

## API Endpoints

### 1. Setup MFA
**POST** `/api/auth/mfa/setup`

Generates a new TOTP secret for the authenticated user.

**Authentication:** Required (Bearer token)

**Response:**
```json
{
  "secret": "BASE32_ENCODED_SECRET",
  "otpAuthUrl": "otpauth://totp/Atlasia:username?secret=...&issuer=Atlasia&algorithm=SHA1&digits=6&period=30",
  "qrCodeDataUri": "data:image/png;base64,..."
}
```

### 2. Verify MFA Setup
**POST** `/api/auth/mfa/verify-setup`

Verifies the TOTP code and activates MFA by storing the secret in the database (encrypted).

**Authentication:** Required (Bearer token)

**Request Body:**
```json
{
  "secret": "BASE32_ENCODED_SECRET",
  "code": "123456"
}
```

**Response:**
```json
{
  "message": "MFA successfully activated",
  "mfaEnabled": true
}
```

### 3. Verify MFA Code (Login)
**POST** `/api/auth/mfa/verify`

Validates the TOTP code during login and returns full authentication tokens.

**Authentication:** Not required (uses MFA token from login)

**Request Body:**
```json
{
  "mfaToken": "SHORT_LIVED_JWT_TOKEN",
  "code": "123456"
}
```

**Response:**
```json
{
  "accessToken": "JWT_ACCESS_TOKEN",
  "refreshToken": "JWT_REFRESH_TOKEN",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

## Login Flow Changes

The existing `/api/auth/login` endpoint now returns different responses based on MFA status:

### User WITHOUT MFA:
Returns standard token response immediately.

### User WITH MFA:
```json
{
  "mfaRequired": true,
  "mfaToken": "SHORT_LIVED_JWT_TOKEN"
}
```

The client must then call `/api/auth/mfa/verify` with the `mfaToken` and TOTP code to complete authentication.

## Security Features

1. **Encrypted Storage:** MFA secrets are stored encrypted in the database using AES-256-GCM
2. **Short-lived MFA Token:** The intermediate MFA token expires in 5 minutes
3. **Rate Limiting:** All authentication endpoints are protected by rate limiting
4. **TOTP Standard:** Uses RFC 6238 compliant TOTP with:
   - SHA1 algorithm
   - 6-digit codes
   - 30-second time step

## Database Schema

The `users` table contains:
- `mfa_secret_encrypted` (TEXT): Encrypted TOTP secret
- MFA is considered enabled when this field is not null/empty

## Implementation Details

### Services
- **MfaService:** Core TOTP operations (generate secret, verify code, generate QR)
- **MfaTokenService:** JWT-based short-lived MFA token management
- **AuthenticationService:** Updated to check MFA status and throw `MfaRequiredException`

### Controllers
- **MfaController:** New controller handling MFA setup and verification
- **AuthController:** Updated login endpoint to handle MFA flow

### DTOs
- **MfaSetupResponse:** Contains secret, OTP auth URL, and QR code
- **MfaVerifySetupRequest:** Secret and code for initial verification
- **MfaVerifyRequest:** MFA token and code for login verification
- **MfaLoginResponse:** Indicates MFA is required with token
- **CurrentUserDto:** Updated to include `mfaEnabled` boolean

## Client Integration

1. **Setup Flow:**
   ```
   User logs in → Calls /mfa/setup → Scans QR code → Calls /mfa/verify-setup with code → MFA activated
   ```

2. **Login Flow (MFA enabled):**
   ```
   User enters credentials → Calls /login → Receives mfaToken → Enters TOTP code → Calls /mfa/verify → Receives tokens
   ```

3. **Check MFA Status:**
   ```
   Call /api/auth/me → Check `mfaEnabled` field in response
   ```
