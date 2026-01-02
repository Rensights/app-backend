# Microsoft 365 Shared Mailbox Configuration Example

## Quick Setup Guide

### Step 1: Create Service Account
1. Go to Microsoft 365 Admin Center → Users → Active users
2. Create a new user: `service-email@yourdomain.com`
3. Set a strong password
4. Enable SMTP AUTH (if required by your organization)

### Step 2: Grant Send As Permission
1. Go to Exchange Admin Center → Recipients → Shared mailboxes
2. Select `no-reply@yourdomain.com` (or create it if it doesn't exist)
3. Click "Manage mailbox delegation"
4. Under "Send As", add `service-email@yourdomain.com`
5. Save and wait 5-10 minutes for permissions to propagate

### Step 3: Update Environment Variables

Update your `app-backend/env-values/dev/backend.yaml`:

```yaml
extraEnv:
  # Email configuration (Microsoft 365 SMTP)
  EMAIL_ENABLED: "true"
  SMTP_HOST: "smtp.office365.com"
  SMTP_PORT: "587"
  SMTP_USERNAME: "service-email@yourdomain.com"  # Service account email
  SMTP_PASSWORD: "<service-account-password>"     # Service account password
  SPRING_MAIL_FROM: "no-reply@yourdomain.com"    # Shared mailbox address
```

### Step 4: Test Configuration

After deploying, check the logs:
```bash
kubectl logs -f <backend-pod-name> | grep EmailService
```

You should see:
```
✅ Email sent successfully to: user@example.com
```

## Alternative: Using Existing Mailbox with Delegation

If you already have a mailbox with a password that has access to send as the shared mailbox:

```yaml
extraEnv:
  EMAIL_ENABLED: "true"
  SMTP_HOST: "smtp.office365.com"
  SMTP_PORT: "587"
  SMTP_USERNAME: "existing-mailbox@yourdomain.com"  # Mailbox with password
  SMTP_PASSWORD: "<existing-mailbox-password>"
  SPRING_MAIL_FROM: "no-reply@yourdomain.com"       # Shared mailbox address
```

## Important Notes

1. **SMTP AUTH**: Some organizations disable SMTP AUTH. If you get authentication errors, you may need to:
   - Enable SMTP AUTH in Exchange Admin Center
   - Or use OAuth2 (see MICROSOFT_365_EMAIL_SETUP.md)

2. **Permissions**: The "Send As" permission can take 5-10 minutes to propagate. Be patient.

3. **Security**: 
   - Never commit passwords to git
   - Use Kubernetes secrets for passwords
   - Rotate passwords regularly

4. **Testing**: Always test in a non-production environment first.

## Troubleshooting

### Error: "Authentication failed"
- Verify username and password
- Check if SMTP AUTH is enabled
- Ensure account is not locked

### Error: "Cannot send as shared mailbox"
- Verify "Send As" permission is granted
- Wait 10 minutes for permissions to propagate
- Check Exchange Admin Center

### Emails not received
- Check spam folder
- Verify SPF/DKIM records for your domain
- Check Microsoft 365 message trace

