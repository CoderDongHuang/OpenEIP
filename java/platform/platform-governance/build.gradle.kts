description = "Tenant-scoped governance contracts for the OpenEIP Java platform"

dependencies {
    implementation(project(":platform-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.3")

    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-mysql")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:mysql:1.21.4")
    testRuntimeOnly("com.mysql:mysql-connector-j")
}
