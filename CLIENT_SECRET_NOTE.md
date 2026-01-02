# Important: Client Secret Value vs Secret ID

## What You Provided
- **Secret ID**: `53b737ee-7a96-449f-95a5-c7348a20a68c`

## What We Need
- **Secret Value**: The actual password/token (a long string)

## The Difference

When you create a client secret in Azure Portal, you get TWO things:

1. **Secret ID** (what you have): `53b737ee-7a96-449f-95a5-c7348a20a68c`
   - This is just an identifier
   - Used for reference/managing the secret
   - **Cannot be used for authentication**

2. **Secret Value** (what we need): `abc123~XYZ789...` (example format)
   - This is the actual password
   - Used for OAuth authentication
   - **Only shown once** when you create it
   - **Cannot be retrieved later**

## What to Do

### Option 1: If You Have the Secret Value
If you copied the secret value when creating it, use that value in:
```yaml
MICROSOFT_CLIENT_SECRET: "<the-actual-secret-value>"
```

### Option 2: If You Only Have the Secret ID
You need to create a NEW client secret:

1. Go to Azure Portal → Your App Registration → **Certificates & secrets**
2. Click **New client secret**
3. Add description and expiration
4. Click **Add**
5. **IMMEDIATELY copy the VALUE** (the long string, not the ID)
6. Use that value in your configuration

**Note**: You can have multiple secrets active at the same time, so creating a new one won't break anything.

## How to Identify the Secret Value

The **Secret Value** will:
- Be much longer than the ID (usually 40+ characters)
- Look like: `abc123~XYZ789-def456-GHI012...`
- Be shown in a yellow/green box when you first create it
- Have a "Copy" button next to it

The **Secret ID** will:
- Be a UUID format: `53b737ee-7a96-449f-95a5-c7348a20a68c`
- Be shown in a table after creation
- Not have a visible value (shows as hidden)

## Configuration

Once you have the **Secret Value**, update:

```yaml
MICROSOFT_CLIENT_SECRET: "<paste-the-secret-value-here>"
```

Do NOT use the Secret ID - it won't work for authentication!

