dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-auth"))
    implementation(project(":platform-knowledge"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:mysql:1.21.4")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.4")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.10.4")
}
