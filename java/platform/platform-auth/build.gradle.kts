plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-common"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.3")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:mysql:1.21.3")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
}

tasks.register<Test>("authBenchmark") {
    description = "Runs the Auth login benchmark and writes its JSON evidence."
    group = "verification"
    useJUnitPlatform {
        includeTags("benchmark")
    }
    systemProperty(
        "authBenchmarkOutput",
        rootProject.layout.projectDirectory.file("../../docs/13-testing/results/auth-benchmark.json").asFile
    )
    shouldRunAfter(tasks.test)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
}
