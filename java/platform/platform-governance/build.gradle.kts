description = "Tenant-scoped governance contracts for the OpenEIP Java platform"

dependencies {
    implementation(project(":platform-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.3")
}
