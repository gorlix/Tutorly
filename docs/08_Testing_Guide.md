# 🧪 Testing Guide

Comprehensive guide for testing the Tutorly application including unit tests, integration tests, and end-to-end testing strategies.

---

**Document**: 08_Testing_Guide.md  
**Last Updated**: September 8, 2026  
**Version**: 1.0.0  
**Author**: Tutorly Development Team  

---

## 📋 Table of Contents

- [Overview](#overview)
- [Testing Strategy](#testing-strategy)
- [Java Backend Testing](#java-backend-testing)
- [Node.js Frontend Testing](#nodejs-frontend-testing)
- [Database Testing](#database-testing)
- [API Testing](#api-testing)
- [Integration Testing](#integration-testing)
- [End-to-End Testing](#end-to-end-testing)
- [Test Coverage](#test-coverage)
- [Continuous Integration](#continuous-integration)
- [Best Practices](#best-practices)

---

## Overview

The Tutorly project implements a comprehensive testing strategy covering multiple layers:

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test interactions between components
- **API Tests**: Test REST API endpoints
- **E2E Tests**: Test complete user workflows
- **Database Tests**: Test data persistence and queries

### Testing Philosophy

- ✅ Write tests before fixing bugs (TDD approach)
- ✅ Maintain minimum 70% code coverage
- ✅ Test critical paths thoroughly
- ✅ Keep tests independent and repeatable
- ✅ Use meaningful test names
- ✅ Mock external dependencies

---

## Testing Strategy

### Test Pyramid

```
           /\
          /  \    E2E Tests (Few, slow, expensive)
         /____\
        /      \  Integration Tests (Some, medium speed)
       /________\
      /          \ Unit Tests (Many, fast, cheap)
     /____________\
```

### Coverage Goals

| Component | Target Coverage | Current Status |
|-----------|----------------|----------------|
| Java Backend | 80% | 🟡 In Progress - service/controller tests exist for every entity (`User`, `Student`, `Admin`, `Lesson`, `Prenotation`, `Test`, `CalendarNote`, `Pack`, `PushSubscription` - 139 tests total, see `src/test/java/.../{service,controller}/`); no repository-layer tests yet (see below), no coverage tool wired up |
| Node.js Frontend | 70% | 🔴 Planned |
| Service Modules | 85% | 🟡 In Progress |
| API Endpoints | 90% | 🟡 In Progress |

---

## Java Backend Testing

### Setup

The Java backend uses **JUnit 5**, **Mockito**, and **Spring Boot Test** for testing - all already wired up in `pom.xml`, nothing to add.

**⚠️ Spring Boot version note:** this project pins `spring-boot-starter-parent` to **4.0.1**, which restructured the test starters into smaller, per-layer artifacts instead of the single classic `spring-boot-starter-test`. It also moved `@WebMvcTest` to a new package and replaced `@MockBean` with `@MockitoBean`. If you're used to Spring Boot 2.x/3.x tutorials (including older revisions of this guide), the imports below are the ones that actually compile against 4.0.1 - verified by running the real test suite, not just reading the docs.

**⚠️ Jackson 3, not Jackson 2 (for `ObjectMapper` specifically):** Spring Boot 4's default JSON engine is **Jackson 3** (`tools.jackson.*`), pulled in via `spring-boot-starter-jackson`/`spring-boot-starter-jackson-test`. Entity annotations (`@JsonProperty`, `@JsonBackReference`, `@JsonIgnoreProperties`, etc.) still come from `com.fasterxml.jackson.annotation.*` - that package didn't move - but if a controller test needs to serialize a request body (e.g. `@Autowired ObjectMapper` to build a POST/PUT JSON payload), import `tools.jackson.databind.ObjectMapper`, **not** `com.fasterxml.jackson.databind.ObjectMapper`. The latter compiles fine (a Jackson 2 `jackson-databind` jar is also on the classpath, transitively, for unrelated reasons) but has no matching Spring-managed bean in a `@WebMvcTest` slice, so `@Autowired` fails at context-startup with `NoSuchBeanDefinitionException`. Found by hitting exactly this error while writing `LessonControllerTest`.

#### Dependencies (already in pom.xml)

```xml
<dependencies>
    <!-- Pulls in JUnit 5, Mockito, AssertJ, and @DataJpaTest support -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Pulls in JUnit 5, Mockito, AssertJ (again, transitively), and @WebMvcTest/MockMvc support -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

No H2 (or any other in-memory database) dependency is used: pure service-layer tests mock the repository with Mockito instead of hitting a database at all, and `@WebMvcTest` slices don't touch persistence either - only the pre-existing `@SpringBootTest` context-load test (`BackendApiApplicationTests`) needs a real database, and it uses the same Postgres instance the app itself connects to (see `application.properties`), not an embedded one.

### Unit Testing

#### Repository Tests

**⚠️ Illustrative, not yet in the codebase** - no repository-layer test exists today (the real suite so far only covers the service/controller layers, see below). Import paths shown are correct for Spring Boot 4.0.1's restructured test autoconfiguration (`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`), but this specific example hasn't been run.

```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StudentRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @Test
    void shouldFindStudentById() {
        // Given
        Student student = new Student();
        student.setName("John");
        student.setSurname("Doe");
        student = studentRepository.save(student);

        // When
        Optional<Student> found = studentRepository.findById(student.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("John", found.get().getName());
    }

    @Test
    void shouldFindStudentsByClass() {
        // Given
        Student student1 = createStudent("Alice", "Smith", "3A");
        Student student2 = createStudent("Bob", "Jones", "3A");
        studentRepository.saveAll(Arrays.asList(student1, student2));

        // When - the real repository method is findByStudentClass, not findByClasse
        List<Student> students = studentRepository.findByStudentClass("3A");

        // Then
        assertEquals(2, students.size());
    }
}
```

#### Service Tests

**✅ Real, verified example** - condensed from `src/test/java/.../service/StudentServiceTest.java`, part of the actual test suite covering the User/Student/Admin anonymize-on-erase feature (see [01_Java_Backend_API.md - Erasure](01_Java_Backend_API.md#erasure-gdpr-style-delete-instead-of-hard-delete)). `StudentService` has no plain "create" method - `saveStudent(Student)` handles both create and update, same as most services in this codebase.

```java
@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentService studentService;

    @Test
    void eraseStudent_scrubsIdentifyingFields() {
        // Given
        Student student = new Student("Marco", "Rossi", "3A", "Excellent in mathematics", "ACTIVE");
        student.setId(7L);
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Student erased = studentService.eraseStudent(student);

        // Then
        assertThat(erased.getName()).isEqualTo("Erased");
        assertThat(erased.getSurname()).isEqualTo("Student 7");
        assertThat(erased.getStatus()).isEqualTo("BLOCKED");
    }
}
```

#### Controller Tests

**✅ Real, verified example** - condensed from `src/test/java/.../controller/StudentControllerTest.java`. Two things that trip people up coming from older Spring Boot tutorials: `@WebMvcTest` now lives under `org.springframework.boot.webmvc.test.autoconfigure`, and `@MockBean` was replaced by Spring Framework's own `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`) - `@MockBean` doesn't exist in this project's Spring Boot version at all. Every `/api/**` route also goes through `ApiKeyInterceptor` (see `config/WebConfig.java`), so a request without a valid `X-API-Key` header never reaches the controller - `@TestPropertySource` pins a known test key rather than depending on the real one in `application.properties`.

```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(StudentController.class)
@TestPropertySource(properties = "api.security.keys=test-api-key")
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private UserService userService; // every @Autowired field on StudentController needs a bean

    @Test
    void eraseStudent_studentDoesNotExist_returns404() throws Exception {
        when(studentService.getStudentById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/students/99").header("X-API-Key", "test-api-key"))
                .andExpect(status().isNotFound());
    }
}
```

### Running Tests

```bash
# Run all tests
cd Java/backend-api
./mvnw test

# Run specific test class
./mvnw test -Dtest=StudentServiceTest

# Run every service-layer test, or every controller-layer test
./mvnw test -Dtest='com.tutorly.app.backend_api.service.*Test'
./mvnw test -Dtest='com.tutorly.app.backend_api.controller.*Test'
```

**⚠️ No coverage report yet** - the `jacoco:report` goal previously shown here doesn't work: the JaCoCo Maven plugin isn't configured in `pom.xml`. Coverage numbers in the table above are targets, not measurements.

---

## Node.js Frontend Testing

### Setup

The Node.js frontend uses **Jest** and **Supertest** for testing.

#### Installation

```bash
cd Nodejs
npm install --save-dev jest supertest @types/jest
```

#### Configuration (package.json)

```json
{
  "scripts": {
    "test": "jest",
    "test:watch": "jest --watch",
    "test:coverage": "jest --coverage"
  },
  "jest": {
    "testEnvironment": "node",
    "coverageDirectory": "coverage",
    "collectCoverageFrom": [
      "src/**/*.js",
      "server_utilities/**/*.js",
      "!src/index.js"
    ]
  }
}
```

### Unit Testing

#### Service Module Tests

```javascript
// server_utilities/__tests__/passwordService.test.js
const passwordService = require('../passwordService');

describe('passwordService', () => {
    describe('hashPassword', () => {
        it('should hash a plain text password', async () => {
            const password = 'mySecretPassword123';
            const hashedPassword = await passwordService.hashPassword(password);
            
            expect(hashedPassword).toBeDefined();
            expect(hashedPassword).not.toBe(password);
            expect(hashedPassword.startsWith('$2b$')).toBe(true);
        });
    });
    
    describe('comparePassword', () => {
        it('should return true for matching passwords', async () => {
            const password = 'testPassword123';
            const hashedPassword = await passwordService.hashPassword(password);
            
            const isMatch = await passwordService.comparePassword(password, hashedPassword);
            
            expect(isMatch).toBe(true);
        });
        
        it('should return false for non-matching passwords', async () => {
            const hashedPassword = await passwordService.hashPassword('correctPassword');
            
            const isMatch = await passwordService.comparePassword('wrongPassword', hashedPassword);
            
            expect(isMatch).toBe(false);
        });
    });
});
```

#### Logger Tests

```javascript
// server_utilities/__tests__/logger.test.js
const logger = require('../logger');

describe('logger', () => {
    let consoleSpy;
    
    beforeEach(() => {
        consoleSpy = jest.spyOn(console, 'log').mockImplementation();
    });
    
    afterEach(() => {
        consoleSpy.mockRestore();
    });
    
    it('should log success messages in green', () => {
        logger.logSuccess('Test message', '127.0.0.1', 'testUser');
        
        expect(consoleSpy).toHaveBeenCalled();
        expect(consoleSpy.mock.calls[0][0]).toContain('[SUCCESS]');
    });
    
    it('should log error messages in red', () => {
        logger.logError('Error occurred', '127.0.0.1', 'testUser');
        
        expect(consoleSpy).toHaveBeenCalled();
        expect(consoleSpy.mock.calls[0][0]).toContain('[ERROR]');
    });
});
```

### Integration Tests

```javascript
// __tests__/routes.test.js
const request = require('supertest');
const app = require('../src/index'); // Export app from index.js

describe('Authentication Routes', () => {
    describe('POST /login', () => {
        it('should login with valid credentials', async () => {
            const response = await request(app)
                .post('/login')
                .send({
                    username: 'tutor1',
                    password: 'password123'
                });
            
            expect(response.status).toBe(302); // Redirect
            expect(response.headers['set-cookie']).toBeDefined();
        });
        
        it('should reject invalid credentials', async () => {
            const response = await request(app)
                .post('/login')
                .send({
                    username: 'invalid',
                    password: 'wrong'
                });
            
            expect(response.status).toBe(302);
            // Should redirect back to login
        });
    });
});
```

### Running Tests

> **Note**: Frontend testing is currently a Work In Progress (WIP). The following commands outline the planned implementation.

```bash
# Run all tests (WIP - not yet implemented)
# npm test

# Run with coverage (WIP)
# npm run test:coverage

# Run in watch mode (WIP)
# npm run test:watch

# Run specific test file (WIP)
# npm test -- passwordService.test.js
```

---

## Database Testing

### Test Database Setup

Create a separate test database configuration:

```properties
# src/test/resources/application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

### Integration Tests with Database

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LessonIntegrationTest {
    
    @Autowired
    private LessonRepository lessonRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void shouldCreateLessonWithRelationships() {
        // Given
        Student student = createAndSaveStudent();
        User tutor = createAndSaveTutor();
        
        Lesson lesson = new Lesson();
        lesson.setStudent(student);
        lesson.setTutor(tutor);
        lesson.setData("2026-02-25");
        
        // When
        Lesson saved = lessonRepository.save(lesson);
        
        // Then
        assertNotNull(saved.getId());
        assertEquals(student.getId(), saved.getStudent().getId());
        assertEquals(tutor.getId(), saved.getTutor().getId());
    }
}
```

---

## API Testing

### Manual API Testing

#### Using curl

```bash
# Test GET endpoint
curl -X GET https://localhost:8443/api/students \
  -H "X-API-Key: your-api-key" \
  -k

# Test POST endpoint
curl -X POST https://localhost:8443/api/students \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John",
    "surname": "Doe",
    "classe": "3A"
  }' \
  -k
```

#### Using test-api.sh

```bash
cd Java/backend-api
./test-api.sh
```

### Automated API Testing

#### Postman/Newman

1. Create Postman collection with all API endpoints
2. Export collection and environment
3. Run with Newman:

```bash
npm install -g newman
newman run api-tests.postman_collection.json -e test-environment.json
```

#### REST Assured (Java)

```java
@Test
void shouldGetStudentById() {
    given()
        .header("X-API-Key", API_KEY)
        .pathParam("id", 1)
    .when()
        .get("/api/students/{id}")
    .then()
        .statusCode(200)
        .body("name", equalTo("John"))
        .body("surname", equalTo("Doe"));
}
```

---

## Integration Testing

### Full Stack Integration Tests

```javascript
describe('Full User Flow', () => {
    let agent;
    
    beforeAll(async () => {
        // Start both servers
        await startJavaBackend();
        await startNodeFrontend();
        agent = request.agent(app);
    });
    
    it('should complete full lesson booking flow', async () => {
        // 1. Login
        await agent.post('/login')
            .send({ username: 'tutor1', password: 'pass123' })
            .expect(302);
        
        // 2. Navigate to lessons page
        const lessonsPage = await agent.get('/lessons').expect(200);
        expect(lessonsPage.text).toContain('Lessons');
        
        // 3. Create new lesson
        const createResponse = await agent.post('/api/lessons/new')
            .send({
                studentId: 1,
                date: '2026-02-26',
                subject: 'Math'
            })
            .expect(200);
        
        expect(createResponse.body.success).toBe(true);
    });
});
```

---

## End-to-End Testing

### Setup with Playwright

```bash
npm install --save-dev @playwright/test
npx playwright install
```

### E2E Test Example

```javascript
// e2e/login.spec.js
const { test, expect } = require('@playwright/test');

test.describe('Login Flow', () => {
    test('should login as tutor', async ({ page }) => {
        await page.goto('http://localhost:3000');
        
        // Fill login form
        await page.fill('input[name="username"]', 'tutor1');
        await page.fill('input[name="password"]', 'password123');
        await page.click('button[type="submit"]');
        
        // Verify redirect to home
        await expect(page).toHaveURL(/.*\/home/);
        await expect(page.locator('h1')).toContainText('Dashboard');
    });
    
    test('should display error for invalid credentials', async ({ page }) => {
        await page.goto('http://localhost:3000/login');
        
        await page.fill('input[name="username"]', 'invalid');
        await page.fill('input[name="password"]', 'wrong');
        await page.click('button[type="submit"]');
        
        // Should show error message
        await expect(page.locator('.error-message')).toBeVisible();
    });
});
```

---

## Test Coverage

### Generating Coverage Reports

#### Java (JaCoCo)

**Not set up yet** - the JaCoCo Maven plugin isn't in `pom.xml`, so `jacoco:report` isn't a working goal today. To add it, add the `jacoco-maven-plugin` (with `prepare-agent` bound to a phase before tests run, and `report` bound after) to `pom.xml`'s `<build><plugins>`; then:

```bash
cd Java/backend-api
./mvnw test jacoco:report
open target/site/jacoco/index.html
```

#### Node.js (Jest)

```bash
npm run test:coverage
open coverage/lcov-report/index.html
```

### Coverage Thresholds

```json
// package.json
{
  "jest": {
    "coverageThreshold": {
      "global": {
        "branches": 70,
        "functions": 70,
        "lines": 70,
        "statements": 70
      }
    }
  }
}
```

---

## Continuous Integration

### GitHub Actions Workflow

```yaml
# .github/workflows/test.yml
name: Run Tests

on: [push, pull_request]

jobs:
  backend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run Backend Tests
        run: |
          cd Java/backend-api
          ./mvnw test
          
  frontend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      - name: Install dependencies
        run: |
          cd Nodejs
          npm install
      - name: Run Frontend Tests (WIP)
        run: |
          cd Nodejs
          # npm test (Not yet implemented)
```

---

## Best Practices

### General Testing Principles

1. **Keep tests independent**: Each test should run in isolation
2. **Use descriptive names**: Test names should explain what they test
3. **Follow AAA pattern**: Arrange, Act, Assert
4. **Test edge cases**: Don't just test happy paths
5. **Mock external dependencies**: Database, APIs, file system
6. **Keep tests fast**: Unit tests should run in milliseconds
7. **Maintain test data**: Use factories or fixtures for test data

### Test Naming Convention

```java
// Good
@Test
void shouldReturnEmptyListWhenNoStudentsExist() { }

@Test
void shouldThrowExceptionWhenStudentNotFound() { }

// Bad  
@Test
void test1() { }

@Test
void testStudent() { }
```

### Test Organization

```
Java/backend-api/src/test/java/
├── com/tutorly/app/backend_api/
│   ├── BackendApiApplicationTests.java  # context-load smoke test (pre-existing)
│   ├── controller/          # Controller tests - one per entity's REST controller
│   │                        # (User, Student, Admin, Lesson, Prenotation, Test,
│   │                        # CalendarNote, Pack, PushSubscription)
│   ├── service/             # Service tests - one per entity's service, same list
│   │                        # (PackServiceTest/CalendarNoteServiceTest are the two
│   │                        # with real business logic to test; the rest are mostly
│   │                        # thin repository pass-throughs)
│   ├── repository/          # Repository tests - not created yet, see note below
│   └── integration/         # Integration tests - not created yet

Nodejs/
├── __tests__/               # Integration tests
├── server_utilities/__tests__/  # Unit tests for services
└── e2e/                     # E2E tests
```

**Why no repository-layer tests yet:** `@DataJpaTest` needs a database to actually run queries against, and this project has no embedded test database (no H2 or similar - see the Setup section above). Without one, `@DataJpaTest` either fails outright (no embedded driver on the classpath) or - via `@AutoConfigureTestDatabase(replace = Replace.NONE)` - falls back to whatever `application.properties` points at, which today is the same real Postgres database the app itself uses. Neither is acceptable to add casually: the first doesn't work, the second would write and delete real rows in a real (dev) database on every test run. Adding a proper embedded test database is a reasonable next step, but it's an infrastructure decision (which database, whether to keep parity with Postgres-specific behavior) that's out of scope for just writing more tests.

---

**Navigation**  
⬅️ **Previous**: [07_Database_Configuration.md](07_Database_Configuration.md) | **Next**: [09_Deployment_Guide.md](09_Deployment_Guide.md) ➡️  
🏠 **Home**: [Documentation Index](README.md)

---

**Last Updated**: September 8, 2026
