CREATE TABLE billing_account (
    account_id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(255),
    provider VARCHAR(20),
    customer_id VARCHAR(255),
    subscription_id VARCHAR(255),
    plan_id VARCHAR(255),
    status VARCHAR(32),
    current_period_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_billing_account_customer_id ON billing_account(customer_id);
