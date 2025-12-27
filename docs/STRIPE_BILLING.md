# Stripe Billing Integration

This guide covers setting up and testing the Stripe billing integration in the Platform Service.

## Architecture Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Frontend  │────▶│   Gateway   │────▶│  Platform   │
│             │     │   Service   │     │   Service   │
└─────────────┘     └─────────────┘     └─────────────┘
                                               │
                    ┌─────────────┐             │
                    │   Stripe    │◀───────────┘
                    │   (SaaS)    │
                    └─────────────┘
                           │
                           ▼ Webhooks
                    ┌─────────────┐
                    │  Platform   │
                    │   Service   │
                    │ /billing/   │
                    │  webhook    │
                    └─────────────┘
```

## Prerequisites

- Docker & Docker Compose
- [Stripe CLI](https://stripe.com/docs/stripe-cli) (for local webhook testing)
- Stripe account (free test mode)

---

## 1. Stripe Dashboard Setup

### 1.1 Get API Keys

1. Go to [Stripe Dashboard](https://dashboard.stripe.com/test/apikeys)
2. Copy your **Secret key** (starts with `sk_test_`)
3. Save it for the `.env` file

### 1.2 Create Products and Prices

Navigate to **Products** in Stripe Dashboard and create:

| Product Name | Price (Monthly) | Notes |
|-------------|-----------------|-------|
| Starter     | $9/month        | For small teams (up to 5 users) |
| Pro         | $29/month       | For growing teams (up to 50 users) |
| Enterprise  | $99/month       | Unlimited users |

For each product:
1. Click **Add Product**
2. Enter name and description
3. Add a recurring price
4. Copy the **Price ID** (starts with `price_`)

---

## 2. Environment Configuration

Add these variables to your `.env` file:

```bash
# Stripe Configuration
STRIPE_API_KEY=sk_test_your_secret_key_here
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret_here

# Stripe Price IDs (from Dashboard)
STRIPE_PRICE_STARTER=price_xxxxxxxxxxxxx
STRIPE_PRICE_PRO=price_yyyyyyyyyyyyy
STRIPE_PRICE_ENTERPRISE=price_zzzzzzzzzzzzz

# Redirect URLs (adjust for production)
STRIPE_SUCCESS_URL=http://localhost:4200/settings/billing?success=true
STRIPE_CANCEL_URL=http://localhost:4200/settings/billing?canceled=true

# Enable/disable billing
BILLING_ENABLED=true
BILLING_B2C_ENABLED=false
```

---

## 3. Local Development Setup

### 3.1 Start Services

```bash
cd /Users/pawan.yadav/prototype/AWS-Infra

# Fresh start
docker-compose down -v
docker-compose up -d

# Verify platform-service is running
docker logs platform-service 2>&1 | grep -i "Started PlatformServiceApplication"
```

### 3.2 Install Stripe CLI

```bash
# macOS
brew install stripe/stripe-cli/stripe

# Login to Stripe
stripe login
```

### 3.3 Forward Webhooks Locally

```bash
# Start webhook forwarding (via Gateway)
stripe listen --forward-to localhost:8080/platform-service/billing/webhook

# OR direct to platform-service (bypasses Gateway)
stripe listen --forward-to localhost:8082/billing/webhook

# Output will show:
# > Ready! Your webhook signing secret is whsec_xxxxx
# Copy this secret to your .env as STRIPE_WEBHOOK_SECRET
```

Keep this terminal running during testing.

---

## 4. API Endpoints

### Base URL
- **Local**: `http://localhost:8082`
- **Via Gateway**: `http://localhost:8083/platform`

### Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/v1/billing/status` | GET | Required | Get subscription status |
| `/api/v1/billing/checkout` | POST | Required | Create checkout session |
| `/api/v1/billing/portal` | POST | Required | Create customer portal |
| `/billing/webhook` | POST | None* | Stripe webhooks |

*Webhook endpoint uses Stripe signature verification instead of JWT.

---

## 5. Testing

### 5.1 Get Subscription Status

```bash
curl -X GET http://localhost:8082/api/v1/billing/status \
  -H "X-Tenant-Id: your-tenant-id" \
  -H "X-Email: user@example.com"
```

**Response:**
```json
{
  "status": "TRIAL",
  "tier": null,
  "currentPeriodEnd": null,
  "trialEndsAt": "2025-02-01T00:00:00Z",
  "hasActiveSubscription": false
}
```

### 5.2 Create Checkout Session

```bash
curl -X POST http://localhost:8082/api/v1/billing/checkout \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: your-tenant-id" \
  -H "X-Email: user@example.com" \
  -d '{"tier": "starter"}'
```

**Response:**
```json
{
  "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_...",
  "sessionId": "cs_test_..."
}
```

Open `checkoutUrl` in browser to complete payment.

### 5.3 Test Card Numbers

Use these test cards in Stripe Checkout:

| Card Number | Scenario |
|-------------|----------|
| `4242 4242 4242 4242` | Successful payment |
| `4000 0000 0000 3220` | 3D Secure required |
| `4000 0000 0000 9995` | Declined (insufficient funds) |

Use any future expiry date and any 3-digit CVC.

### 5.4 Verify Webhook Processing

After completing checkout:

```bash
# Check platform-service logs
docker logs platform-service 2>&1 | grep -E "webhook|Checkout completed"

# Verify database updated
docker exec -it postgres psql -U postgres -d awsinfra -c \
  "SELECT id, name, subscription_status, stripe_subscription_id FROM tenant LIMIT 5;"
```

### 5.5 Create Customer Portal Session

```bash
curl -X POST http://localhost:8082/api/v1/billing/portal \
  -H "X-Tenant-Id: your-tenant-id"
```

**Response:**
```json
{
  "portalUrl": "https://billing.stripe.com/p/session/..."
}
```

Portal allows customers to:
- Update payment method
- View invoices
- Cancel subscription

---

## 6. Webhook Events Handled

| Event | Action |
|-------|--------|
| `checkout.session.completed` | Activate subscription, update tenant tier |
| `customer.subscription.updated` | Sync subscription status |
| `customer.subscription.deleted` | Mark as cancelled |
| `invoice.paid` | Log payment success |
| `invoice.payment_failed` | Mark as past_due |

---

## 7. Database Schema

### stripe_customers
Maps tenants to Stripe customer IDs.

```sql
SELECT * FROM stripe_customers WHERE tenant_id = 'xxx';
```

### webhook_events
Tracks processed events for idempotency.

```sql
SELECT * FROM webhook_events ORDER BY created_at DESC LIMIT 10;
```

### tenant (billing fields)
```sql
SELECT id, subscription_status, stripe_subscription_id, 
       stripe_price_id, current_period_end 
FROM tenant WHERE stripe_subscription_id IS NOT NULL;
```

---

## 8. Configuration Reference

All billing settings in `application.yml`:

```yaml
billing:
  enabled: true
  b2c:
    enabled: false  # Enable to charge B2C users
  stripe:
    api-key: ${STRIPE_API_KEY}
    webhook-secret: ${STRIPE_WEBHOOK_SECRET}
    success-url: ${STRIPE_SUCCESS_URL}
    cancel-url: ${STRIPE_CANCEL_URL}
  tiers:
    starter:
      price-id: ${STRIPE_PRICE_STARTER}
      name: Starter
      max-users: 5
    pro:
      price-id: ${STRIPE_PRICE_PRO}
      name: Pro
      max-users: 50
    enterprise:
      price-id: ${STRIPE_PRICE_ENTERPRISE}
      name: Enterprise
      max-users: -1  # Unlimited
```

---

## 9. Troubleshooting

### Webhook signature verification failed
```
Invalid signature
```
**Fix**: Ensure `STRIPE_WEBHOOK_SECRET` matches Stripe CLI output.

### No billing record for tenant
```
No billing record for tenant: xxx
```
**Fix**: Complete checkout first to create Stripe customer record.

### Checkout returns 400
```
Unknown tier: xxx
```
**Fix**: Check tier name matches config (`starter`, `pro`, `enterprise`).

---

## 10. Production Checklist

- [ ] Replace test API keys with live keys
- [ ] Configure production webhook endpoint in Stripe Dashboard
- [ ] Use real Price IDs from production products
- [ ] Update success/cancel URLs to production frontend
- [ ] Configure Gateway to allow webhook endpoint without auth
- [ ] Set up AlertManager for `invoice.payment_failed` events
