# SMTP Authentication Troubleshooting Guide

## Current Error
```
jakarta.mail.AuthenticationFailedException
java.net.SocketTimeoutException: Read timed out
```

## Common Causes and Solutions

### 1. SMTP AUTH Not Enabled (Most Common)
Microsoft 365 may have SMTP AUTH disabled by default for security reasons.

**Solution:**
1. Go to Microsoft 365 Admin Center
2. Navigate to **Settings** → **Org settings** → **Modern authentication**
3. Or use PowerShell:
   ```powershell
   Connect-ExchangeOnline
   Set-CASMailbox -Identity "info@rensights.com" -SmtpClientAuthenticationDisabled $false
   ```

### 2. Multi-Factor Authentication (MFA) Enabled
If MFA is enabled on the account, you cannot use regular password authentication.

**Solutions:**
- **Option A:** Disable MFA for the service account (not recommended for security)
- **Option B:** Create an App Password:
  1. Go to https://account.microsoft.com/security
  2. Sign in with `info@rensights.com`
  3. Go to **Security** → **Advanced security options**
  4. Under **App passwords**, create a new app password
  5. Use this app password instead of the regular password

### 3. Account Locked or Blocked
The account might be temporarily locked due to failed login attempts.

**Solution:**
1. Check Microsoft 365 Admin Center → **Users** → **Active users**
2. Look for `info@rensights.com` and check if it's blocked
3. Unblock if necessary
4. Wait 15-30 minutes for lockout to expire

### 4. Incorrect Credentials
Double-check the username and password.

**Solution:**
- Verify username: `info@rensights.com` (not `info@rensights.com@rensights.com`)
- Verify password: `V.573300278010aj` (check for typos, extra spaces)
- Try logging into Outlook Web App with these credentials to verify they work

### 5. Firewall/Network Issues
The server might not be able to reach `smtp.office365.com:587`.

**Solution:**
1. Test connectivity from the server:
   ```bash
   telnet smtp.office365.com 587
   # or
   nc -zv smtp.office365.com 587
   ```
2. Check if outbound port 587 is open
3. Verify DNS resolution works

### 6. Conditional Access Policies
Your organization might have conditional access policies blocking SMTP.

**Solution:**
1. Check Azure AD → **Security** → **Conditional Access**
2. Look for policies that might block SMTP access
3. Add an exception for the service account or SMTP protocol

## Quick Fixes Applied

1. **Increased Timeouts**: Changed from 5 seconds to 30 seconds
2. **Disabled Mail Health Check**: Prevents log spam while troubleshooting
3. **Added SSL Socket Factory**: Ensures proper SSL/TLS connection

## Testing Steps

1. **Verify credentials work:**
   ```bash
   # Test with telnet (if available)
   telnet smtp.office365.com 587
   EHLO test
   AUTH LOGIN
   # Enter base64 encoded username
   # Enter base64 encoded password
   ```

2. **Check application logs** for more detailed error messages

3. **Test from a different location** to rule out network issues

## Alternative: Use App Password

If MFA is enabled, create an App Password:

1. Sign in to https://account.microsoft.com/security
2. Go to **Security** → **Advanced security options**
3. Click **Create a new app password**
4. Use this password in your configuration instead of the regular password

## Configuration Update Needed

After fixing the issue, update the environment variables:

```yaml
SMTP_USERNAME: "info@rensights.com"
SMTP_PASSWORD: "<app-password-if-mfa-enabled>"
SPRING_MAIL_FROM: "no-reply@rensights.com"
```

## Next Steps

1. Check if SMTP AUTH is enabled for `info@rensights.com`
2. Verify if MFA is enabled (if yes, create App Password)
3. Check if account is locked/blocked
4. Test connectivity to `smtp.office365.com:587`
5. Review Azure AD conditional access policies

