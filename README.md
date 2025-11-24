# AWS-Infra

This project's documentation has been consolidated. Please refer to:

*   **[HLD.md](HLD.md)**: High-Level Design, Architecture, and Requirements.
*   **[copilot-index.md](copilot-index.md)**: Technical Index, Setup Guide, and Current Status.
*   **[next_task.md](next_task.md)**: Roadmap and Active Tasks.


NOTE:
# Normal build (skips system tests) ✅
mvn clean package

# Run E2E tests explicitly
export TEST_USER_EMAIL="test@example.com"
export TEST_USER_PASSWORD="Test123!"
docker-compose up -d
mvn verify -Psystem-tests
