# Slack Bot Application Test Suite Documentation

## Overview

This test suite provides comprehensive validation of the Slack Bot workflow with mocked Slack calls, stubbed external API responses, and precise markdown accuracy verification.

## Testing Guide

This document provides comprehensive testing strategies for the Java Slack Bot App, including unit tests, integration tests, contract tests, and end-to-end testing with Docker.

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
- **`ExternalServiceClientTest`** - Tests config-driven API client
- **`SlackServiceTest`** - Tests Slack integration
- **`MarkdownRendererTest`** - Tests JSON to Markdown conversion

#### Controller Tests
- **`HealthControllerTest`** - Health endpoint testing
- **`WorkflowControllerIntegrationTest`** - API endpoint testing

#### Configuration Tests
- **`SlackConfigTest`** - Configuration binding validation

### Integration Tests (`src/test/java/org/mveeprojects/integration/`)

- **`CompleteWorkflowAccuracyTest`** - End-to-end workflow testing
- Tests the complete flow from API call to Slack message posting

### Contract Tests (`src/test/java/org/mveeprojects/contract/`)

- **`ExternalApiContractTest`** - Validates external API contracts
- Ensures compatibility with real external services

### Performance Tests (`src/test/java/org/mveeprojects/performance/`)

- **`PerformanceTest`** - Load and performance testing
- Tests concurrent API calls and timeout handling

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
./gradlew test --tests="*Test" --exclude-tasks="*IntegrationTest"

# Integration tests only
./gradlew test --tests="*IntegrationTest"

# Performance tests only
./gradlew test --tests="*PerformanceTest"

# Contract tests only
./gradlew test --tests="*ContractTest"
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

## 🚀 Continuous Integration Testing

### GitHub Actions Example
```yaml
name: Test Suite
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      
      - name: Start test environment
        run: docker-compose up -d
      
      - name: Wait for services
        run: sleep 30
      
      - name: Run tests
        run: ./gradlew test
      
      - name: Run integration tests
        run: ./gradlew test --tests="*IntegrationTest"
      
      - name: Cleanup
        run: docker-compose down
```

## 📊 Test Data and Fixtures

### Mock Response Examples

#### Primary API Response
```json
{
  "service": "Primary Data Service",
  "timestamp": "2025-10-07 14:30:00",
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
    }
  }
}
```

#### Secondary API Response
```json
{
  "service": "Secondary Analytics Service",
  "analytics": {
    "page_views": {
      "today": 15678,
      "this_week": 98234
    },
    "user_engagement": {
      "bounce_rate": "32.5%",
      "session_duration": "4m 23s"
    }
  }
}
```

## 🛠️ Testing Tools and Utilities

### Test Runner Script
Use the included `test-runner.sh` script for comprehensive testing:

```bash
# Run all test categories
./test-runner.sh

# Run specific test type
./test-runner.sh --type=integration

# Run with Docker environment
./test-runner.sh --docker
```

### Custom Test Annotations
```java
@ConfigDrivenTest  // Tests config-driven functionality
@SlackIntegrationTest  // Tests Slack integration
@ExternalApiTest  // Tests external API interactions
```

## 🔧 Troubleshooting Tests

### Common Issues

1. **WireMock containers not ready**
   ```bash
   # Check container status
   docker-compose ps
   
   # Check logs
   docker-compose logs primary-api-mock
   ```

2. **Port conflicts**
   ```bash
   # Check port usage
   lsof -i :8080,8081,8082
   
   # Stop conflicting services
   docker-compose down
   ```

3. **Test timeouts**
   - Increase timeout values in test configuration
   - Check network connectivity between containers

### Debug Mode Testing
```bash
# Run tests with debug output
./gradlew test --debug --info

# Run specific test with verbose output
./gradlew test --tests="ExternalServiceClientTest" --info
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

### Monitoring Test Results
```bash
# Generate test report
./gradlew test jacocoTestReport

# View results
open build/reports/tests/test/index.html
open build/reports/jacoco/test/html/index.html
```
