# Stripe Invoice System Setup Guide

## Overview
The system automatically creates Stripe customers on registration and sends invoices via email when customers pay. All invoices are stored in the database and displayed in the account page.

## Automatic Invoice Email Setup

### Stripe Dashboard Configuration
1. Go to Stripe Dashboard → Settings → Billing → Invoices
2. Ensure "Automatically send invoices" is enabled
3. Configure invoice email template if needed (optional)

**Note**: By default, Stripe automatically sends invoices via email when payment succeeds. No additional code configuration is needed.

## Webhook Setup

### 1. Configure Webhook in Stripe Dashboard
1. Go to Stripe Dashboard → Developers → Webhooks
2. Click "Add endpoint"
3. **IMPORTANT**: Set endpoint URL to the BACKEND API host (not frontend):
   - Development: `http://dev-api.72.62.40.154.nip.io:31416/api/webhooks/stripe`
   - ⚠️ DO NOT use `dev.72.62.40.154.nip.io` (frontend host)
   - ✅ USE `dev-api.72.62.40.154.nip.io` (backend API host)
4. Select events to listen for:
   - `invoice.payment_succeeded`
   - `invoice.created`
   - `invoice.updated`
   - `invoice.payment_failed` (optional)
5. Copy the webhook signing secret

### 2. Configure Webhook Secret
The webhook secret is configured in:
- `env-values/dev/backend.yaml`: `STRIPE_WEBHOOK_SECRET: "whsec_ao4ndqNEH0oM9X8gohCPlGoJGm3I2UhW"`
- `application-dev.yml`: `stripe.webhook-secret: ${STRIPE_WEBHOOK_SECRET:}`

**Webhook Endpoint URL:**
- Development: `http://dev-api.72.62.40.154.nip.io:31416/api/webhooks/stripe`
  - ✅ Backend API host: `dev-api.72.62.40.154.nip.io`
  - ❌ Frontend host (wrong): `dev.72.62.40.154.nip.io`
- Update production URL when deploying to production

### Troubleshooting 404 Errors
If you get a 404 error with HTML response:
- ✅ Check that webhook URL uses the **backend API host** (`dev-api.*`)
- ❌ Do NOT use the frontend host (`dev.*` - this will return Next.js 404 page)
- Verify the backend service is running and accessible
- Check Kong ingress routing is properly configured

## How It Works

### Registration Flow
1. User registers → Stripe customer created automatically
2. Stripe customer ID stored in `user.stripeCustomerId`

### Payment Flow
1. User pays via Stripe Checkout
2. Stripe creates invoice automatically
3. **Stripe sends invoice email to customer** (automatic)
4. Webhook receives `invoice.payment_succeeded` event
5. Invoice stored in database via `InvoiceService.processStripeInvoice()`
6. Invoice appears in account page automatically

### Invoice Display
- Invoices are displayed in `/account` page
- Users can download PDF or view hosted invoice
- "Sync Invoices" button manually refreshes from Stripe
- Invoices automatically synced after successful checkout

## API Endpoints

### Invoice Endpoints
- `GET /api/invoices` - Get all user invoices (requires auth)
- `POST /api/invoices/sync` - Sync invoices from Stripe (requires auth)
- `GET /api/invoices/{id}` - Get specific invoice (requires auth)

### Webhook Endpoint
- `POST /api/webhooks/stripe` - Stripe webhook handler (public, signature verified)

## Database Schema

The `invoices` table stores:
- Stripe invoice ID (unique)
- User ID (foreign key)
- Stripe customer ID
- Amount, currency, status
- Invoice PDF URL
- Invoice hosted URL
- Invoice number
- Dates (invoice date, due date, paid at)

## Testing

### Local Testing with Stripe CLI
```bash
# Install Stripe CLI
brew install stripe/stripe-cli/stripe

# Login
stripe login

# Forward webhooks to local server
stripe listen --forward-to localhost:8080/api/webhooks/stripe

# Trigger test event
stripe trigger invoice.payment_succeeded
```

## Production Checklist

- [ ] Configure Stripe webhook endpoint URL in Stripe Dashboard
- [ ] Set `STRIPE_WEBHOOK_SECRET` in production secrets
- [ ] Verify "Automatically send invoices" is enabled in Stripe Dashboard
- [ ] Test webhook receives events (check logs)
- [ ] Verify invoices appear in account page after payment
- [ ] Test invoice email delivery

