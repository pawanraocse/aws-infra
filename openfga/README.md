# OpenFGA Authorization Model

This directory contains the OpenFGA authorization model for fine-grained permissions.

## Files

- `model.fga` - Authorization model in OpenFGA DSL format
- `model.json` - Auto-generated JSON format (for API)

## Quick Start

### 1. Enable OpenFGA

Uncomment the `openfga` service in `docker-compose.yml`:

```bash
docker-compose up openfga
```

### 2. Access Playground

Open http://localhost:3000 to use the OpenFGA Playground UI.

### 3. Create Store and Apply Model

```bash
# Create a store
curl -X POST http://localhost:8090/stores \
  -H "Content-Type: application/json" \
  -d '{"name": "saas-template"}'

# Store the returned store_id, then apply the model
FGA_STORE_ID=<store_id>
fga model write --store-id $FGA_STORE_ID --file model.fga
```

## Model Overview

```
organization
    └── admin, member

project
    ├── owner, editor, viewer
    └── inherits from: organization (admin, member)

folder
    ├── owner, editor, viewer
    └── inherits from: project (editor, viewer)

document
    ├── owner, editor, viewer
    └── inherits from: folder (editor, viewer)
```

## Example Tuples

```bash
# User is admin of organization
user:alice -> admin -> organization:acme

# User is editor on project
user:bob -> editor -> project:project-123

# User is viewer on specific folder
user:charlie -> viewer -> folder:folder-456

# Folder belongs to project (enables inheritance)
project:project-123 -> project -> folder:folder-456
```

## Permission Checks

```bash
# Check if user can view document
curl "http://localhost:8090/stores/$FGA_STORE_ID/check" \
  -H "Content-Type: application/json" \
  -d '{
    "tuple_key": {
      "user": "user:alice",
      "relation": "can_view",
      "object": "document:doc-789"
    }
  }'
```
