import java.util.UUID

plugins {
    id("java")
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.2.1"

    // Generates test coverage reports and verifies the coverage threshold.
    jacoco
}

group = "com.ionixx"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        // Compile and run the application using Java 21.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Application dependencies
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // Structured JSON logging
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Swagger UI and OpenAPI documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

    // JWT: API at compile time, implementation and JSON serializer at runtime.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // PostgreSQL database driver
    runtimeOnly("org.postgresql:postgresql")

    // Development tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Testing support
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // Testcontainers for PostgreSQL integration tests.
    // The BOM keeps the Testcontainers modules on compatible versions.
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Fixed jar name so the Dockerfile can copy build/libs/app.jar.
tasks.bootJar {
    archiveFileName.set("app.jar")
}

// Spotless: `./gradlew spotlessCheck` (CI) and `./gradlew spotlessApply` (auto-fix).
// Line endings follow .gitattributes (LF), so Windows and Linux agree.
spotless {
    java {
        target("src/**/*.java")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Use JUnit 5 for all Gradle test tasks.
tasks.withType<Test> {
    useJUnitPlatform()

    // Same timezone on every machine (Windows reports the legacy name "Asia/Calcutta",
    // which the postgres:16 image rejects).
    jvmArgs("-Duser.timezone=Asia/Kolkata")

    // application.properties requires JWT_SECRET. If it is not set (for example in CI),
    // use a throwaway random value so nothing secret is stored in the repository.
    if (System.getenv("JWT_SECRET").isNullOrBlank()) {
        environment("JWT_SECRET", "${UUID.randomUUID()}${UUID.randomUUID()}")
    }
}

// JaCoCo generates code-coverage data from the test execution.
jacoco {
    toolVersion = "0.8.14"
}

// Automatically generate the HTML and XML coverage reports after tests.
tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Fail the verification task if overall line coverage is below 80%.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            element = "BUNDLE"

            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// Make the normal Gradle check task enforce the coverage threshold.
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}