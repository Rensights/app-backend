# Microsoft 365 Shared Mailbox Email Setup Guide

## Overview
Microsoft 365 shared mailboxes don't have passwords. To send emails from a shared mailbox (e.g., `no-reply@yourdomain.com`), you have several options:

## Option 1: Use a Service Account (Recommended - Simplest)

This is the easiest approach if you have a service account with a password.

### Steps:
1. **Create or use a service account** in Microsoft 365 Admin Center
   - Go to Microsoft 365 Admin Center → Users → Active users
   - Create a new user account (e.g., `service-account@yourdomain.com`)
   - Assign it a license and set a password
   - Enable SMTP AUTH for this account (if required)

2. **Grant Send As permission** to the service account for the shared mailbox:
   - Go to Exchange Admin Center → Recipients → Shared mailboxes
   - Select your shared mailbox (e.g., `no-reply@yourdomain.com`)
   - Click "Manage mailbox delegation"
   - Under "Send As", add the service account
   - Save changes

3. **Configure SMTP settings** in your application:
   ```yaml
   spring:
     mail:
       host: smtp.office365.com
       port: 587
       username: service-account@yourdomain.com  # Service account email
       password: <service-account-password>      # Service account password
       properties:
         mail:
           smtp:
             auth: true
             starttls:
               enable: true
               required: true
   ```

4. **Set the "From" address** to the shared mailbox:
   ```yaml
   spring:
     mail:
       from: no-reply@yourdomain.com  # Shared mailbox address
   ```

## Option 2: Use OAuth2 with Microsoft Graph API (Advanced)

This approach uses OAuth2 authentication and doesn't require a password.

### Prerequisites:
- Azure AD app registration
- Client ID and Client Secret
- Tenant ID

### Steps:

1. **Register an application in Azure AD:**
   - Go to Azure Portal → Azure Active Directory → App registrations
   - Click "New registration"
   - Name: "Rensights Email Service"
   - Supported account types: Single tenant
   - Redirect URI: Not needed for service-to-service auth
   - Click "Register"

2. **Create a Client Secret:**
   - Go to "Certificates & secrets"
   - Click "New client secret"
   - Description: "Email Service Secret"
   - Expires: Choose appropriate duration
   - Click "Add" and **copy the secret value** (you won't see it again)

3. **Grant API Permissions:**
   - Go to "API permissions"
   - Click "Add a permission"
   - Select "Microsoft Graph"
   - Choose "Application permissions"
   - Add: `Mail.Send` and `Mail.ReadWrite`
   - Click "Add permissions"
   - Click "Grant admin consent"

4. **Add dependencies** to `pom.xml`:
   ```xml
   <dependency>
       <groupId>com.microsoft.graph</groupId>
       <artifactId>microsoft-graph</artifactId>
       <version>5.42.0</version>
   </dependency>
   <dependency>
       <groupId>com.azure</groupId>
       <artifactId>azure-identity</artifactId>
       <version>1.9.0</version>
   </dependency>
   ```

5. **Update configuration** in `application-dev.yml`:
   ```yaml
   microsoft:
     graph:
       tenant-id: ${MICROSOFT_TENANT_ID:}
       client-id: ${MICROSOFT_CLIENT_ID:}
       client-secret: ${MICROSOFT_CLIENT_SECRET:}
       from-email: ${MICROSOFT_FROM_EMAIL:no-reply@yourdomain.com}
   ```

6. **Update EmailService** to use Microsoft Graph API (see implementation below)

## Option 3: Use SMTP with Delegated Access

If you have a regular mailbox with a password that has delegated access to send as the shared mailbox:

1. **Grant Send As permission** (same as Option 1, step 2)

2. **Configure SMTP** with the regular mailbox credentials:
   ```yaml
   spring:
     mail:
       host: smtp.office365.com
       port: 587
       username: regular-mailbox@yourdomain.com  # Regular mailbox with password
       password: <regular-mailbox-password>
       from: no-reply@yourdomain.com             # Shared mailbox address
   ```

## Environment Variables

For **Option 1** (Service Account - Recommended):
```bash
SMTP_HOST=smtp.office365.com
SMTP_PORT=587
SMTP_USERNAME=service-account@yourdomain.com
SMTP_PASSWORD=<service-account-password>
SPRING_MAIL_FROM=no-reply@yourdomain.com
EMAIL_ENABLED=true
```

For **Option 2** (OAuth2):
```bash
MICROSOFT_TENANT_ID=<your-tenant-id>
MICROSOFT_CLIENT_ID=<your-client-id>
MICROSOFT_CLIENT_SECRET=<your-client-secret>
MICROSOFT_FROM_EMAIL=no-reply@yourdomain.com
EMAIL_ENABLED=true
```

## Testing

After configuration, test the email service:
1. Check application logs for email sending attempts
2. Verify emails are received
3. Check spam folder if emails don't arrive
4. Verify "From" address shows the shared mailbox address

## Troubleshooting

### Common Issues:

1. **"Authentication failed"**
   - Verify username and password are correct
   - Check if SMTP AUTH is enabled for the account
   - Ensure account is not blocked

2. **"Cannot send as shared mailbox"**
   - Verify "Send As" permission is granted
   - Wait a few minutes for permissions to propagate
   - Check Exchange Admin Center permissions

3. **"Connection timeout"**
   - Verify firewall allows outbound connections to `smtp.office365.com:587`
   - Check if your IP is not blocked by Microsoft

4. **Emails going to spam**
   - Set up SPF, DKIM, and DMARC records for your domain
   - Use a verified domain in Microsoft 365

## Security Notes

- **Never commit passwords or secrets to version control**
- Use environment variables or secret management systems
- Rotate passwords/secrets regularly
- Use least privilege principle for service accounts
- Enable MFA for service accounts (if possible)

