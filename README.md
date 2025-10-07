# Java Slack Bot App

A config-driven Spring Boot application that integrates with Slack workflows and dynamically calls multiple external REST APIs based on YAML configuration.

## 🚀 Features

- **Config-Driven Architecture**: Add/remove external APIs through simple YAML configuration
- **Slack Integration**: Posts API responses as threaded messages in Slack channels
- **Dynamic Service Discovery**: Automatically processes all configured external services
- **Flexible API Support**: Custom headers, timeouts, and retry logic per service
- **Docker Ready**: Complete Docker Compose setup with WireMock for testing
- **Markdown Rendering**: Converts JSON responses to formatted Slack messages
- **Error Handling**: Graceful degradation and retry mechanisms
- **Java 21 Ready**: Built with modern Java 21 features and syntax

## 🏗️ Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Slack API     │◄──►│  Spring Boot App │◄──►│ External APIs   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                       ┌──────▼──────┐
                       │ Config-Driven│
                       │   Services   │
                       └─────────────┘
```

## 🔧 Configuration

### External Services Configuration

Configure multiple external APIs in `application.yml`:

```yaml
external:
  services:
    - name: "primary-api"
      url: ${EXTERNAL_SERVICE_PRIMARY_URL:http://localhost:8081/api/primary}
      display-name: "Primary Data Service"
      timeout: 5000
      retry-attempts: 3
      headers:
        Authorization: "Bearer ${PRIMARY_API_TOKEN:}"
        Content-Type: "application/json"
    - name: "secondary-api"
      url: ${EXTERNAL_SERVICE_SECONDARY_URL:http://localhost:8082/api/secondary}
      display-name: "Secondary Analytics Service"
      timeout: 8000
      retry-attempts: 2
      headers:
        X-API-Key: "${SECONDARY_API_KEY:}"
        Content-Type: "application/json"
```

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `SLACK_BOT_TOKEN` | Slack bot OAuth token | Yes |
| `SLACK_SIGNING_SECRET` | Slack app signing secret | Yes |
| `EXTERNAL_SERVICE_PRIMARY_URL` | Primary API endpoint | No (has default) |
| `EXTERNAL_SERVICE_SECONDARY_URL` | Secondary API endpoint | No (has default) |
| `PRIMARY_API_TOKEN` | Authentication token for primary API | No |
| `SECONDARY_API_KEY` | API key for secondary service | No |

## 🚀 Quick Start

### Using Docker Compose (Recommended)

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd JavaSlackBotApp
   ```

2. **Set environment variables**
   ```bash
   export SLACK_BOT_TOKEN="xoxb-your-bot-token"
   export SLACK_SIGNING_SECRET="your-signing-secret"
   ```

3. **Start all services**
   ```bash
   docker-compose up -d
   ```

4. **Verify services are running**
   ```bash
   curl http://localhost:8080/api/workflow/services
   ```

### Local Development

1. **Prerequisites**
   - Java 21
   - Gradle 7+

2. **Build and run**
   ```bash
   ./gradlew build
   ./gradlew bootRun
   ```

## 📡 API Endpoints

### Workflow Execution

- **`POST /api/workflow/execute`** - Execute all configured services
  ```json
  {
    "channel": "C1234567890",
    "threadTs": "1234567890.123456"
  }
  ```

- **`POST /api/workflow/execute/services`** - Execute specific services
  ```json
  {
    "channel": "C1234567890",
    "threadTs": "1234567890.123456",
    "serviceNames": ["primary-api", "secondary-api"]
  }
  ```

- **`POST /api/workflow/trigger`** - Legacy endpoint for backward compatibility
  ```json
  {
    "channel": "C1234567890",
    "threadTs": "1234567890.123456"
  }
  ```

### Slack Integration

- **`POST /slack/events`** - Handles Slack events (app mentions, URL verification)
- **`POST /slack/commands`** - Handles Slack slash commands (`/ping`, `/workflow`)

### Service Management

- **`GET /api/workflow/services`** - List all configured services
- **`GET /health`** - Application health check

## 🧪 Testing with WireMock

The Docker Compose setup includes WireMock containers that simulate external APIs:

### Available Mock Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| Primary API | `http://localhost:8081/api/primary` | Returns user metrics and system data |
| Primary API Error | `http://localhost:8081/api/primary/error` | Returns 500 error for testing |
| Secondary API | `http://localhost:8082/api/secondary` | Returns analytics data |
| Secondary API Slow | `http://localhost:8082/api/secondary/slow` | 10-second delay response |
| Secondary API Timeout | `http://localhost:8082/api/secondary/timeout` | 15-second timeout scenario |

### Test Commands

```bash
# Test primary API directly
curl -H "Authorization: Bearer test-token" http://localhost:8081/api/primary

# Test secondary API directly
curl -H "X-API-Key: test-key" http://localhost:8082/api/secondary

# Execute Slack workflow
curl -X POST http://localhost:8080/api/workflow/execute \
  -H "Content-Type: application/json" \
  -d '{"channel":"C1234567890","threadTs":"1234567890.123456"}'
```

## 🔄 Adding New External Services

1. **Add service configuration to `application.yml`**:
   ```yaml
   - name: "new-service"
     url: "https://api.example.com/data"
     display-name: "New Service"
     timeout: 6000
     retry-attempts: 2
     headers:
       Authorization: "Bearer ${NEW_SERVICE_TOKEN:}"
   ```

2. **Set environment variable** (if needed):
   ```bash
   export NEW_SERVICE_TOKEN="your-token"
   ```

3. **Restart the application** - the new service will be automatically discovered and processed!

## 🏗️ Project Structure

```
├── src/main/java/org/mveeprojects/
│   ├── config/
│   │   ├── ExternalServiceConfig.java      # YAML config binding
│   │   ├── SlackConfig.java               # Slack client configuration
│   │   └── SlackProperties.java           # Slack properties binding
│   ├── controller/
│   │   ├── HealthController.java          # Health checks
│   │   ├── SlackEventController.java      # Slack event handling
│   │   └── WorkflowController.java        # Workflow API endpoints
│   └── service/
│       ├── ExternalServiceClient.java     # Config-driven API client
│       ├── MarkdownRenderer.java          # JSON to Markdown conversion
│       ├── SlackService.java              # Slack API integration
│       └── SlackWorkflowService.java      # Main workflow orchestration
├── src/test/java/org/mveeprojects/        # Comprehensive test suite
│   ├── contract/                          # API contract tests
│   ├── integration/                       # End-to-end integration tests
│   ├── performance/                       # Performance and load tests
│   ├── security/                          # Security validation tests
│   └── smoke/                             # Basic smoke tests
├── wiremock/                              # Mock API definitions
│   ├── primary-api/mappings/
│   └── secondary-api/mappings/
├── docker-compose.yml                     # Complete Docker setup
└── Dockerfile                            # Java 21 application container
```

## 🛠️ Technologies Used

- **Java 21** - Latest LTS Java version with modern syntax
- **Spring Boot 3.x** - Application framework
- **Spring WebFlux** - Reactive web client for external APIs
- **Slack API Client** - Direct Slack API integration (simplified approach)
- **Jackson** - JSON processing and data binding
- **Docker & Docker Compose** - Containerization and orchestration
- **WireMock** - API mocking for comprehensive testing
- **Gradle** - Build automation and dependency management
- **JUnit 5** - Testing framework with comprehensive test categories

## 🧪 Testing

The application includes a comprehensive test suite with multiple categories:

- **Unit Tests** - Individual component testing
- **Integration Tests** - Complete workflow validation
- **Contract Tests** - External API compatibility validation
- **Performance Tests** - Load testing and performance validation
- **Security Tests** - Authentication and security validation
- **Edge Case Tests** - Boundary condition and error scenario testing
- **Smoke Tests** - Basic application health and startup testing

Run tests with:
```bash
# All tests
./gradlew test

# Specific test categories
./gradlew test --tests="*IntegrationTest"
./gradlew test --tests="*PerformanceTest"
./gradlew test --tests="*ContractTest"

# With coverage report
./gradlew test jacocoTestReport
```

## 🚀 Slack App Setup

### Required Slack App Permissions

Your Slack app needs the following OAuth scopes:

**Bot Token Scopes:**
- `chat:write` - Send messages
- `chat:write.public` - Send messages to channels the app isn't in
- `app_mentions:read` - Listen for mentions
- `commands` - Handle slash commands

**Event Subscriptions:**
- `app_mention` - React to @mentions
- `message.channels` - Listen to channel messages (optional)

### Slash Commands Setup

Configure these slash commands in your Slack app:
- **`/ping`** - Health check command
  - Request URL: `https://your-app.com/slack/commands`
- **`/workflow`** - Show available workflow endpoints
  - Request URL: `https://your-app.com/slack/commands`

### Event Subscriptions Setup

Set your Request URL to: `https://your-app.com/slack/events`

