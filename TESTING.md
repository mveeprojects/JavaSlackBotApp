# Slack Bot Application Test Suite Documentation

## Overview

This test suite provides comprehensive validation of the Slack Bot workflow with mocked Slack calls, stubbed external API responses, and precise markdown accuracy verification.

## Testing Guide

Comprehensive testing documentation for the Java Slack Bot App, covering unit tests, integration tests, contract tests, performance tests, and end-to-end testing with Docker.

## 🧪 Test Structure

The application follows a comprehensive testing pyramid approach:

```
         ┌─────────────────┐
         │   E2E Tests     │  ← Full Docker environment
         └─────────────────┘
       ┌───────────────────────┐
       │ Integration Tests     │  ← API + Service integration
       └───────────────────────┘
     ┌─────────────────────────────┐
     │     Unit Tests              │  ← Individual components
     └─────────────────────────────┘
```

## 🏗️ Test Categories

### Unit Tests (`src/test/java/org/mveeprojects/`)

#### Service Tests
- **`ExternalServiceClientTest`** - Tests config-driven API client with WireMock
- **`SlackServiceTest`** - Tests Slack API integration with proper mocking
- **`MarkdownRendererTest`** - Tests JSON to Markdown conversion accuracy

#### Controller Tests
- **`HealthControllerTest`** - Health endpoint testing
- **`WorkflowControllerIntegrationTest`** - API endpoint testing with Spring Boot Test

#### Configuration Tests
- **`SlackConfigTest`** - Configuration binding validation with test properties

### Integration Tests (`src/test/java/org/mveeprojects/integration/`)

- **`CompleteWorkflowAccuracyTest`** - End-to-end workflow testing
  - Tests complete flow: External API → JSON processing → Markdown rendering → Slack posting
  - Validates exact markdown format output
  - Uses WireMock for external API simulation

### Contract Tests (`src/test/java/org/mveeprojects/contract/`)

- **`ExternalApiContractTest`** - Validates external API contracts
  - Tests various API response formats (REST, JSON:API, HAL, GraphQL)
  - Validates content type handling
  - Tests error response formats (RFC 7807 Problem Details)

### Performance Tests (`src/test/java/org/mveeprojects/performance/`)

- **`PerformanceTest`** - Load and performance testing
  - Concurrent API call testing (10+ simultaneous requests)
  - Large JSON processing performance
  - Memory usage validation
  - Response time benchmarking

### Security Tests (`src/test/java/org/mveeprojects/security/`)

- **`SecurityValidationTest`** - Security and authentication testing
- Validates API token handling and secure communication

### Edge Case Tests (`src/test/java/org/mveeprojects/edge/`)

- **`EdgeCaseTest`** - Boundary condition and error scenario testing
- Tests malformed responses, network failures, and edge cases

### Smoke Tests (`src/test/java/org/mveeprojects/smoke/`)

- **`ApplicationSmokeTest`** - Basic application startup and health tests

## 🐳 Docker-Based Testing

### Local Testing Environment

Start the complete testing environment:

```bash
# Start all services including WireMock
docker-compose up -d

# Wait for services to be ready
sleep 30

# Run integration tests against Docker environment
./gradlew test --tests="*IntegrationTest"
```

### WireMock Test Scenarios

The Docker setup provides various testing scenarios:

#### 1. Success Scenarios
```bash
# Test primary API success
curl -H "Authorization: Bearer test-token" \
     http://localhost:8081/api/primary

# Test secondary API success  
curl -H "X-API-Key: test-key" \
     http://localhost:8082/api/secondary
```

#### 2. Error Scenarios
```bash
# Test 500 error handling
curl -H "Authorization: Bearer test-token" \
     http://localhost:8081/api/primary/error

# Test timeout handling (15s delay)
curl -H "X-API-Key: test-key" \
     http://localhost:8082/api/secondary/timeout
```

#### 3. Performance Scenarios
```bash
# Test slow response (10s delay)
curl -H "X-API-Key: test-key" \
     http://localhost:8082/api/secondary/slow
```

## 🔧 Running Tests

### All Tests
```bash
./gradlew test
```

### Specific Test Categories
```bash
# Unit tests only
./gradlew test --tests="*Test" --exclude="*IntegrationTest"

# Integration tests only
./gradlew test --tests="*IntegrationTest"

# Performance tests only
./gradlew test --tests="*PerformanceTest"

# Contract tests only
./gradlew test --tests="*ContractTest"

# Security tests only
./gradlew test --tests="*SecurityTest"

# Edge case tests only
./gradlew test --tests="*EdgeCaseTest"

# Smoke tests only
./gradlew test --tests="*SmokeTest"
```

### Test with Coverage
```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

## 🎯 Testing the Config-Driven Architecture

### Adding New Service for Testing

1. **Add test service to `application.yml`**:
```yaml
external:
  services:
    # ...existing services...
    - name: "test-service"
      url: "http://localhost:8083/api/test"
      display-name: "Test Service"
      timeout: 3000
      retry-attempts: 1
      headers:
        X-Test-Header: "test-value"
```

2. **Create WireMock mapping** (`wiremock/test-service/mappings/test.json`):
```json
{
  "request": {
    "method": "GET",
    "url": "/api/test"
  },
  "response": {
    "status": 200,
    "jsonBody": {
      "message": "Test service response",
      "timestamp": "{{now}}"
    }
  }
}
```

3. **Test the new service**:
```bash
# Test service discovery
curl http://localhost:8080/api/workflow/services

# Test specific service execution
curl -X POST http://localhost:8080/api/workflow/execute/services \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "C1234567890",
    "threadTs": "1234567890.123456",
    "serviceNames": ["test-service"]
  }'
```

## 🔍 Test Scenarios

### 1. Happy Path Testing
```bash
# Test complete workflow execution
curl -X POST http://localhost:8080/api/workflow/execute \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "C1234567890",
    "threadTs": "1234567890.123456"
  }'

# Expected: 200 OK with service count
# Expected: Two Slack messages posted to thread
```

### 2. Error Handling Testing
```bash
# Test with invalid channel
curl -X POST http://localhost:8080/api/workflow/execute \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "",
    "threadTs": "1234567890.123456"
  }'

# Expected: 400 Bad Request with error message
```

### 3. Service-Specific Testing
```bash
# Test specific services only
curl -X POST http://localhost:8080/api/workflow/execute/services \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "C1234567890",
    "threadTs": "1234567890.123456",
    "serviceNames": ["primary-api"]
  }'

# Expected: 200 OK with single service execution
```

### 4. Service Discovery Testing
```bash
# List all configured services
curl http://localhost:8080/api/workflow/services

# Expected: JSON array with service configurations
```

### 5. Slack Integration Testing
```bash
# Test Slack event handling
curl -X POST http://localhost:8080/slack/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "url_verification",
    "challenge": "test_challenge"
  }'

# Expected: Returns the challenge value

# Test Slack commands
curl -X POST http://localhost:8080/slack/commands \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "command=/ping"

# Expected: 200 OK with pong response
```

## 🚀 Continuous Integration Testing

### GitHub Actions Example
```yaml
name: Test Suite
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-
      
      - name: Start test environment
        run: docker-compose up -d
      
      - name: Wait for services
        run: sleep 30
      
      - name: Run unit tests
        run: ./gradlew test --tests="*Test" --exclude="*IntegrationTest"
      
      - name: Run integration tests
        run: ./gradlew test --tests="*IntegrationTest"
      
      - name: Run contract tests
        run: ./gradlew test --tests="*ContractTest"
      
      - name: Run performance tests
        run: ./gradlew test --tests="*PerformanceTest"
      
      - name: Generate test report
        run: ./gradlew jacocoTestReport
      
      - name: Upload test results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-results
          path: build/reports/
      
      - name: Cleanup
        run: docker-compose down
```

## 📊 Test Data and Fixtures

### Mock Response Examples

#### Primary API Response
```json
{
  "service": "Primary Data Service",
  "timestamp": "2025-10-07T14:30:00Z",
  "data": {
    "users": {
      "total": 1543,
      "active": 1205,
      "new_today": 23
    },
    "metrics": {
      "response_time": "125ms",
      "uptime": "99.8%",
      "requests_per_minute": 342
    },
    "status": "healthy",
    "version": "1.2.3",
    "environment": "production"
  },
  "metadata": {
    "generated_by": "Primary API Mock",
    "request_id": "12345-67890-abcdef",
    "processing_time": "45ms"
  }
}
```

#### Secondary API Response
```json
{
  "service": "Secondary Analytics Service",
  "timestamp": "2025-10-07T14:30:00Z",
  "analytics": {
    "page_views": {
      "today": 15678,
      "this_week": 98234,
      "this_month": 456789
    },
    "user_engagement": {
      "bounce_rate": "32.5%",
      "session_duration": "4m 23s",
      "pages_per_session": 3.2
    },
    "conversion_metrics": {
      "conversion_rate": "2.8%",
      "total_conversions": 87,
      "revenue": "$12,450.00"
    },
    "traffic_sources": {
      "organic": "45%",
      "direct": "28%",
      "social": "15%",
      "paid": "12%"
    }
  },
  "alerts": [
    {
      "type": "info",
      "message": "Traffic spike detected in organic search"
    },
    {
      "type": "warning", 
      "message": "Conversion rate below target for mobile users"
    }
  ]
}
```

## 🛠️ Testing Tools and Utilities

### Available Testing Dependencies

The project includes these testing libraries:
- **JUnit 5** - Primary testing framework
- **Mockito** - Mocking framework for unit tests
- **Spring Boot Test** - Integration testing with Spring context
- **WireMock** - HTTP service mocking
- **Reactor Test** - Reactive stream testing
- **Testcontainers** - Container-based integration testing (if needed)

### Test Configuration

Tests use these configuration approaches:
- **`@SpringBootTest`** - Full Spring context for integration tests
- **`@WebMvcTest`** - Controller layer testing
- **`@TestPropertySource`** - Override properties for tests
- **`@MockBean`** - Mock Spring beans
- **WireMock Server** - Mock external HTTP services

### Writing New Tests

When adding new tests, follow these patterns:

```java
// Unit Test Example
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock
    private Dependency mockDependency;
    
    @Test
    void testServiceBehavior() {
        // Arrange, Act, Assert
    }
}

// Integration Test Example
@SpringBootTest
@TestPropertySource(properties = {"slack.bot-token=test-token"})
class IntegrationTest {
    @Autowired
    private Service service;
    
    @Test
    void testIntegration() {
        // Test with real Spring context
    }
}
```

## 🔧 Troubleshooting Tests

### Common Issues

1. **WireMock containers not ready**
   ```bash
   # Check container status
   docker-compose ps
   
   # Check logs
   docker-compose logs primary-api-mock
   docker-compose logs secondary-api-mock
   ```

2. **Port conflicts**
   ```bash
   # Check port usage
   lsof -i :8080,8081,8082
   
   # Stop conflicting services
   docker-compose down
   
   # Kill processes using ports
   kill -9 $(lsof -t -i:8080)
   ```

3. **Test timeouts**
   - Increase timeout values in test configuration
   - Check network connectivity between containers
   - Verify WireMock mappings are correct

4. **Slack API mocking issues**
   - Ensure `@MockBean` is used for SlackService in tests
   - Verify proper RequestConfigurator mocking in SlackServiceTest
   - Check that test properties include slack configuration

### Debug Mode Testing
```bash
# Run tests with debug output
./gradlew test --debug --info

# Run specific test with verbose output
./gradlew test --tests="ExternalServiceClientTest" --info

# Run with system properties
./gradlew test -Dtest.debug=true
```

### Test Environment Variables

For local testing, you can override environment variables:

```bash
# Run tests with custom configuration
SLACK_BOT_TOKEN=test-token \
EXTERNAL_SERVICE_PRIMARY_URL=http://localhost:8081/api/primary \
./gradlew test
```

## 📈 Test Metrics and Reporting

### Coverage Goals
- **Unit Tests**: >90% code coverage
- **Integration Tests**: >80% feature coverage
- **End-to-End Tests**: 100% critical path coverage

### Performance Benchmarks
- **API Response Time**: <500ms per service
- **Slack Message Posting**: <2s total workflow
- **Concurrent Service Calls**: Support 10+ simultaneous requests
- **JSON Processing**: Handle 1000+ item arrays within 5s
- **Memory Usage**: <50MB increase during heavy processing

### Monitoring Test Results
```bash
# Generate comprehensive test report
./gradlew test jacocoTestReport

# View HTML reports
open build/reports/tests/test/index.html
open build/reports/jacoco/test/html/index.html

# Generate XML reports for CI
./gradlew test jacocoTestReport --xml

# Check test summary
./gradlew test --continue | grep -E "(PASSED|FAILED|SKIPPED)"
```

### Test Result Analysis

After running tests, check these locations for detailed results:
- `build/reports/tests/test/` - Test execution reports
- `build/reports/jacoco/test/html/` - Code coverage reports  
- `build/test-results/test/` - Raw test result XML files
- Console output for immediate pass/fail status

## 🎯 Best Practices

### Test Organization
- Keep test classes in the same package structure as source classes
- Use descriptive test method names that explain the scenario
- Group related tests using nested test classes (`@Nested`)

### Test Data Management
- Use test-specific configuration files when needed
- Keep test data small and focused
- Use builders or factories for complex test objects

### Mock Usage
- Mock external dependencies, not the class under test
- Use `@MockBean` for Spring-managed dependencies
- Verify mock interactions when behavior matters

### Performance Testing
- Run performance tests in isolation
- Use realistic data sizes and scenarios
- Set reasonable performance expectations
- Monitor memory usage and resource consumption
