# 🚧 Terraform TODO: Production-Ready Cognito Pre Token Generation Lambda Trigger & Robustness

This document tracks all remaining and recommended steps to make the Terraform infrastructure robust, secure, and production-ready, with a focus on adding a Cognito pre token generation Lambda trigger for tenant ID injection.

---

## 1. Cognito Pre Token Generation Lambda Trigger (Tenant ID)

- [ ] **Create Lambda Function**
  - Store handler code in `terraform/lambda/pre-token-generation/handler.mjs` (Node.js 18+, ES module syntax).
  - Ensure code uses the V2 event format for compatibility with Cognito's V2_0 trigger.
  - Add robust error handling, logging, and input validation.

- [ ] **Terraform Lambda Deployment**
  - Use Terraform to package and deploy the Lambda (archive or S3 source).
  - Parameterize environment variables if needed.
  - Enable Lambda versioning and aliasing for safe updates.

- [ ] **IAM Policy and Role**
  - Create a specific IAM policy with least-privilege permissions (CloudWatch logging, etc.).
  - Attach the policy to a dedicated Lambda execution role.
  - Allow Cognito to invoke the Lambda via `lambda:InvokeFunction`.

- [ ] **Cognito User Pool Integration**
  - Attach the Lambda as a pre token generation trigger using the V2 event format.
  - Explicitly set the trigger version to "V2_0 (Basic features + access token customization)" in Terraform.
  - Add Terraform dependencies to ensure the Lambda is created before the user pool.

- [ ] **Configuration and Secrets Management**
  - Store sensitive Lambda environment variables (if any) in AWS SSM Parameter Store or Secrets Manager.
  - Reference these securely in Terraform.

- [ ] **Observability and Monitoring**
  - Enable detailed CloudWatch logging for the Lambda.
  - Set up CloudWatch Alarms for Lambda errors or throttling.
  - Optionally, add X-Ray tracing for debugging.

- [ ] **Documentation and Automation**
  - Document the trigger, event version, and claims in the README.
  - Add deployment and rollback instructions.
  - Automate packaging and deployment (e.g., via CI/CD or scripts).

---

## 2. General Production Readiness Improvements

- [ ] **S3 Backend for State**
  - Move Terraform state to S3 with DynamoDB state locking (see `main.tf` and `REVIEW.md`).

- [ ] **Multi-Environment Support**
  - Separate dev/staging/prod using different `terraform.tfvars` and state files.

- [ ] **Monitoring & Alerting**
  - Add CloudWatch alarms for key resources (Cognito, Lambda, etc.).

- [ ] **Backup & Disaster Recovery**
  - Automate user export and backup strategies for Cognito.

- [ ] **Security Hardening**
  - Review and minimize IAM permissions for all resources.
  - Store all secrets in SSM or Secrets Manager.

- [ ] **Testing & Validation**
  - Add automated tests for Lambda (unit/integration).
  - Validate token claims in downstream services.

- [ ] **Documentation**
  - Update README and SUMMARY.md with new trigger, IAM, and monitoring details.

---

## 3. Optional Advanced Enhancements

- [ ] **Lambda Deployment Optimization**
  - Use S3 for large Lambda packages.
  - Enable canary deployments or blue/green for Lambda updates.

- [ ] **CI/CD Integration**
  - Automate Terraform and Lambda deployments via CI/CD pipeline.

- [ ] **CloudWatch Dashboards**
  - Create dashboards for real-time monitoring.

- [ ] **Custom Domain & SSL**
  - Set up custom domain for Cognito Hosted UI with ACM SSL.

---

## 4. Tracking & Ownership

- [ ] Assign responsible owners and target dates for each item (optional).
- [ ] Integrate this TODO with project management tools if needed.

---

**Last updated:** 2025-11-24

