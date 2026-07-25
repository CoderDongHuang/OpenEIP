dependencies {
    implementation(project(":platform-common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.lettuce:lettuce-core")
    implementation("org.eclipse.angus:angus-mail:2.0.3")

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.3")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.oracle.database.jdbc:ojdbc11")
    runtimeOnly("com.sap.cloud.db.jdbc:ngdbc:2.25.9")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.h2database:h2")
}
