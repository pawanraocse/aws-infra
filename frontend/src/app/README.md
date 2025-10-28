# App Folder Structure
# Cleanup Notice
- core/: Root app config, routes, and shell
- layout/: Layout and shell components
- features/: Domain features
- shared/: Shared utilities/components

All root-level files have been modularized for maintainability.

All root-level files have been moved to the core/ folder for a clean, production-ready structure. Remove the following files from the app/ root:
- app.config.server.ts
- app.config.ts
- app.routes.server.ts
- app.routes.ts
- app.spec.ts
- app.html
- app.scss
- app.ts

Use the core/ folder for all root-level configuration and bootstrapping.
