# Local Development Scripts

This directory is reserved for local development helper scripts that are specific to individual developers' workflows.

## Usage

Add your personal development scripts here. These scripts are typically:
- Environment-specific setups
- Personal productivity tools
- Local testing helpers
- Database seed scripts for local dev

## Guidelines

1. **Do not commit sensitive data** - use environment variables
2. **Document your scripts** - add comments explaining what they do
3. **Keep it optional** - other developers shouldn't depend on your scripts

## Example Scripts

```bash
# Example: Local database reset
./reset-local-db.sh

# Example: Generate test data
./seed-test-data.sh

# Example: Clean Docker volumes
./clean-docker.sh
```
