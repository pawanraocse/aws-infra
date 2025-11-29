# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.3.7.

## Configuration

Before running the application, you need to configure AWS Cognito credentials.

### Automatic Configuration (Recommended)

The Cognito configuration is automatically updated when you deploy the Terraform infrastructure:

```bash
# From the project root
./scripts/terraform/deploy.sh
```

This will:
1. Deploy Cognito infrastructure via Terraform
2. Store configuration in AWS SSM Parameter Store
3. **Automatically update frontend environment files** with the correct values

### Manual Configuration

If needed, you can manually edit `src/environments/environment.development.ts`:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  cognito: {
    userPoolId: 'us-east-1_xxxxxxxxx',      // From terraform output or SSM
    userPoolWebClientId: 'xxxxxxxxxx',      // From terraform output or SSM
    region: 'us-east-1'
  }
};
```

**Fetching from SSM manually:**
```bash
# Get User Pool ID
aws ssm get-parameter --name "/cloud-infra/dev/cognito/user_pool_id" --query 'Parameter.Value' --output text

# Get Client ID  
aws ssm get-parameter --name "/cloud-infra/dev/cognito/client_id" --query 'Parameter.Value' --output text
```

## 🎨 UI/UX & Theming

This application uses a modern design system built on:

- **PrimeNG v20:** UI Component Library
- **Theme:** `Aura` (Premium Modern Theme)
- **Grid System:** `PrimeFlex`
- **Typography:** `Inter` (Google Fonts)
- **Styling:** SCSS with CSS Variables (see `src/styles.scss`)

### Customizing the Theme
The theme is configured in `src/app/app.config.ts`. You can change the preset or customize colors in `src/styles.scss`.

```typescript
providePrimeNG({
    theme: {
        preset: Aura
    }
})
```

## Development server

To start a local development server, run:

```bash
npm start
# or
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
