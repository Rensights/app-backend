# Microsoft Graph API Email Setup Guide

## Overview
This guide explains how to set up Microsoft Graph API for sending emails from `no-reply@rensights.com` using the provided Azure AD application credentials.

## Prerequisites
- Azure AD application registered with the following credentials:
  - **Application (client) ID**: `70a6e6ef-c980-47dc-89ea-70ebd47fd56d`
  - **Directory (tenant) ID**: `e84f3a33-9e68-4a68-aae3-bebba66459e6`
  - **Client Secret**: (Must be created in Azure Portal)

## Step 1: Create Client Secret in Azure Portal

1. Go to [Azure Portal](https://portal.azure.com)
2. Navigate to **Azure Active Directory** → **App registrations**
3. Find your application (Client ID: `70a6e6ef-c980-47dc-89ea-70ebd47fd56d`)
4. Go to **Certificates & secrets**
5. Click **New client secret**
6. Add a description (e.g., "Email Service Secret")
7. Choose expiration period
8. Click **Add**
9. **IMPORTANT**: Copy the **Secret Value** (not the Secret ID!) immediately (you won't see it again!)
   - **Secret ID**: `53b737ee-7a96-449f-95a5-c7348a20a68c` (this is just an identifier)
   - **Secret Value**: A long string like `abc123~XYZ...` (this is what you need!)

**⚠️ If you only have the Secret ID**: You need to create a NEW client secret and copy the VALUE this time. The secret value cannot be retrieved after creation.

## Step 2: Grant API Permissions

1. In the same app registration, go to **API permissions**
2. Click **Add a permission**
3. Select **Microsoft Graph**
4. Choose **Application permissions** (not Delegated)
5. Add the following permissions:
   - `Mail.Send` - Send mail as any user
   - `Mail.ReadWrite` - Read and write mail in all mailboxes
6. Click **Add permissions**
7. **IMPORTANT**: Click **Grant admin consent for [Your Organization]**
   - This step is required for application permissions to work

## Step 3: Grant Mailbox Permissions

The application needs permission to send emails from `no-reply@rensights.com`:

1. Go to [Exchange Admin Center](https://admin.exchange.microsoft.com)
2. Navigate to **Recipients** → **Mailboxes**
3. Find `no-reply@rensights.com` (or the mailbox you want to send from)
4. Click on it, then go to **Mailbox permissions**
5. Under **Send As**, add the service principal:
   - The service principal name format: `{Application Name}@{Tenant ID}`
   - Or use PowerShell:
     ```powershell
     Connect-ExchangeOnline
     Add-RecipientPermission -Identity "no-reply@rensights.com" -Trustee "70a6e6ef-c980-47dc-89ea-70ebd47fd56d" -AccessRights SendAs -Confirm:$false
     ```

## Step 4: Configure Environment Variables

Update your Kubernetes secrets or environment variables:

```yaml
MICROSOFT_TENANT_ID: "e84f3a33-9e68-4a68-aae3-bebba66459e6"
MICROSOFT_CLIENT_ID: "70a6e6ef-c980-47dc-89ea-70ebd47fd56d"
MICROSOFT_CLIENT_SECRET: "<your-client-secret-from-step-1>"
MICROSOFT_FROM_EMAIL: "no-reply@rensights.com"
EMAIL_ENABLED: "true"
EMAIL_USE_GRAPH_API: "true"
```

## Step 5: Create Kubernetes Secret (Recommended)

For production, store the client secret in Kubernetes:

```bash
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=client-secret='<your-client-secret>' \
  --namespace=<your-namespace>
```

Then reference it in your Helm values:
```yaml
extraEnv:
  MICROSOFT_CLIENT_SECRET:
    valueFrom:
      secretKeyRef:
        name: microsoft-graph-credentials
        key: client-secret
```

## How It Works

1. **Authentication**: Uses Client Credentials flow (OAuth 2.0)
   - No user interaction required
   - Perfect for service-to-service communication

2. **Email Sending**: Uses Microsoft Graph API `sendMail` endpoint
   - Sends from: `no-reply@rensights.com`
   - Uses the mailbox associated with the `fromEmail` configuration

3. **Fallback**: If Graph API fails, automatically falls back to SMTP

## Testing

After deployment, test the email service:

1. Trigger a verification email (signup/login)
2. Check application logs for:
   ```
   ✅ Email sent successfully via Microsoft Graph to: user@example.com
   ```
3. Verify the email is received and shows `no-reply@rensights.com` as sender

## Troubleshooting

### Error: "Insufficient privileges to complete the operation"
- **Solution**: Grant admin consent for API permissions (Step 2)

### Error: "Mailbox not found"
- **Solution**: Ensure `no-reply@rensights.com` mailbox exists and is accessible

### Error: "Access denied"
- **Solution**: Grant "Send As" permission (Step 3)

### Error: "Invalid client secret"
- **Solution**: Verify the client secret is correct and not expired

## Benefits of Microsoft Graph API

1. **No SMTP AUTH required** - Works without enabling SMTP authentication
2. **No MFA issues** - Uses application credentials, not user credentials
3. **Better security** - OAuth 2.0 with client credentials
4. **More reliable** - Direct API integration with Microsoft 365
5. **Better monitoring** - Can track emails via Graph API

## References

- [Microsoft Graph API Documentation](https://docs.microsoft.com/en-us/graph/api/user-sendmail)
- [Azure AD App Registration Guide](https://docs.microsoft.com/en-us/azure/active-directory/develop/quickstart-register-app)
- [Blog: Integrate Outlook Mail Java Spring Boot](https://devot.team/blog/integrate-outlook-mail-java-spring-boot)

