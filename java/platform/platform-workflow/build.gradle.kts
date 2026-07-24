dependencies {
    implementation(project(":platform-common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.3")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:mysql:1.21.3")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
}

tasks.register<Test>("workflowBenchmark") {
    description = "Runs the durable workflow transition benchmark."
    group = "verification"
    useJUnitPlatform { includeTags("benchmark") }
    systemProperty(
        "workflowBenchmarkOutput",
        rootProject.layout.projectDirectory.file("../../docs/13-testing/results/workflow-benchmark.json").asFile
    )
    maxHeapSize = "256m"
    maxParallelForks = 1
    jvmArgs("-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xss512k")
    shouldRunAfter(tasks.test)
}

tasks.test {
    useJUnitPlatform { excludeTags("benchmark") }
    maxHeapSize = "256m"
    maxParallelForks = 1
    jvmArgs("-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xss512k")
}
