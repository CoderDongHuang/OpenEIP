description = "Tenant-scoped Marketplace catalog and publication contracts"

dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-governance"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-jdbc")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.3")
    testImplementation("com.h2database:h2")
}
