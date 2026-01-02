# Fix Microsoft Graph API "Access Denied" Error

## Current Error

```
Error code: ErrorAccessDenied
Error message: Access is denied. Check credentials and try again.
```

This means the service principal doesn't have permission to send emails as `no-reply@rensights.com`.

## Solution: Grant "Send As" Permission

You need to grant the service principal permission to send emails from the `no-reply@rensights.com` mailbox.

### Option 1: Using Exchange Admin Center (Easiest)

1. Go to [Exchange Admin Center](https://admin.exchange.microsoft.com)
2. Sign in with an admin account that has permissions to manage mailboxes
3. Navigate to **Recipients** → **Mailboxes**
4. Search for and click on `no-reply@rensights.com`
5. In the mailbox properties, go to **Mailbox permissions** tab
6. Under **Send As**, click **Edit**
7. Click **Add** and search for your application:
   - Application Name: Look for an app with Client ID `70a6e6ef-c980-47dc-89ea-70ebd47fd56d`
   - Or search for the service principal name
8. Select it and click **Add**
9. Click **Save**

### Option 2: Using PowerShell (Recommended for Automation)

1. Connect to Exchange Online:
   ```powershell
   Connect-ExchangeOnline
   ```

2. Grant "Send As" permission:
   ```powershell
   # Get the service principal
   $servicePrincipal = Get-MgServicePrincipal -Filter "appId eq '70a6e6ef-c980-47dc-89ea-70ebd47fd56d'"
   
   # Grant Send As permission
   Add-RecipientPermission -Identity "no-reply@rensights.com" `
     -Trustee $servicePrincipal.UserPrincipalName `
     -AccessRights SendAs `
     -Confirm:$false
   ```

   Or if you know the service principal name:
   ```powershell
   Add-RecipientPermission -Identity "no-reply@rensights.com" `
     -Trustee "YourAppName@e84f3a33-9e68-4a68-aae3-bebba66459e6" `
     -AccessRights SendAs `
     -Confirm:$false
   ```

### Option 3: Using Microsoft Graph API (Alternative)

If the above methods don't work, you can also grant permissions via Microsoft Graph API:

```powershell
# Connect to Microsoft Graph
Connect-MgGraph -Scopes "User.Read.All", "Application.Read.All", "Mail.Send"

# Get service principal
$sp = Get-MgServicePrincipal -Filter "appId eq '70a6e6ef-c980-47dc-89ea-70ebd47fd56d'"

# Grant Send As permission (requires Exchange Admin role)
# This is typically done via Exchange Admin Center or Exchange PowerShell
```

## Verify API Permissions

Also ensure the application has the required API permissions:

1. Go to [Azure Portal](https://portal.azure.com)
2. Navigate to **Azure Active Directory** → **App registrations**
3. Find your application (Client ID: `70a6e6ef-c980-47dc-89ea-70ebd47fd56d`)
4. Go to **API permissions**
5. Verify you have:
   - ✅ `Mail.Send` (Application permission) - **Admin consent granted**
   - ✅ `Mail.ReadWrite` (Application permission) - **Admin consent granted**
6. If not granted, click **Grant admin consent for [Your Organization]**

## Verify Mailbox Exists

Ensure the mailbox `no-reply@rensights.com` exists:

1. Go to [Exchange Admin Center](https://admin.exchange.microsoft.com)
2. Navigate to **Recipients** → **Mailboxes**
3. Search for `no-reply@rensights.com`
4. If it doesn't exist, create it:
   - Go to **Recipients** → **Shared mailboxes** (if it's a shared mailbox)
   - Or create a user mailbox if needed

## Alternative: Use a Different Mailbox

If you can't grant permissions to `no-reply@rensights.com`, you can:

1. Use a different mailbox that you have access to
2. Update the `MICROSOFT_FROM_EMAIL` environment variable
3. Grant "Send As" permission for that mailbox instead

## After Granting Permissions

1. Wait 5-10 minutes for permissions to propagate
2. Restart the backend deployment:
   ```bash
   kubectl rollout restart deployment backend -n dev
   ```
3. Test the forgot-password endpoint again
4. Check logs to verify:
   ```bash
   kubectl logs -n dev -l app.kubernetes.io/name=backend --tail=50 | grep -i "microsoft graph"
   ```

You should see:
```
✅ Email sent successfully via Microsoft Graph to: user@example.com
```

## Troubleshooting

### Still Getting "Access Denied"

1. **Check if permissions propagated**: Wait 10-15 minutes and try again
2. **Verify service principal name**: The service principal might have a different name format
3. **Check mailbox type**: Shared mailboxes might need different permissions
4. **Verify admin consent**: API permissions must have admin consent granted

### Can't Find Service Principal in Exchange Admin Center

The service principal might not appear in the Exchange Admin Center UI. Use PowerShell instead:

```powershell
# Get all service principals
Get-MgServicePrincipal | Where-Object { $_.AppId -eq "70a6e6ef-c980-47dc-89ea-70ebd47fd56d" }

# Note the UserPrincipalName or DisplayName
# Then use it in Add-RecipientPermission
```

### Need Help Finding Service Principal Name

Run this PowerShell command:

```powershell
Connect-MgGraph -Scopes "Application.Read.All"
$sp = Get-MgServicePrincipal -Filter "appId eq '70a6e6ef-c980-47dc-89ea-70ebd47fd56d'"
$sp | Select-Object DisplayName, UserPrincipalName, Id
```

Use the `UserPrincipalName` or `Id` in the `Add-RecipientPermission` command.

