import org.gradle.api.tasks.testing.Test

description = "Tenant-scoped governance contracts for the OpenEIP Java platform"

dependencies {
    implementation(project(":platform-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework:spring-jdbc")

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.4")

    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-mysql")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:mysql:1.21.4")
    testRuntimeOnly("com.mysql:mysql-connector-j")
}

tasks.register<Test>("governanceQuotaBenchmark") {
    description = "Runs the runtime quota admission benchmark and writes its JSON evidence."
    group = "verification"
    useJUnitPlatform { includeTags("benchmark") }
    systemProperty(
        "governanceQuotaBenchmarkOutput",
        rootProject.layout.projectDirectory.file("../../docs/13-testing/results/v0.7-governance-quota-benchmark.json").asFile
    )
    maxParallelForks = 1
    shouldRunAfter(tasks.test)
}

tasks.test {
    useJUnitPlatform { excludeTags("benchmark") }
}
