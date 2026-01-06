# Amplify Module

AWS Amplify for Angular frontend hosting with CI/CD.

## Features

- ✅ **CI/CD** - Auto-build on push
- ✅ **SPA routing** - Proper 404 handling for Angular
- ✅ **Custom domains** - Optional domain configuration
- ✅ **PR previews** - Optional preview environments
- ✅ **Free tier** - 5GB storage, 15GB/month bandwidth

## Usage

```hcl
module "amplify" {
  source = "../../modules/amplify"

  project_name = "saas-factory"
  environment  = "production"

  repository_url      = "https://github.com/user/repo"
  github_access_token = var.github_token
  branch_name         = "main"
  app_name            = "frontend"

  environment_variables = {
    ANGULAR_APP_API_URL = "https://api.example.com"
  }

  # Optional: Custom domain
  domain_name      = "example.com"
  subdomain_prefix = "app"
}
```

## Cost

| Feature | Free Tier |
|---------|-----------|
| Build minutes | 1000/month |
| Storage | 5GB |
| Bandwidth | 15GB/month |

**Budget deployment cost: $0** (within free tier)
