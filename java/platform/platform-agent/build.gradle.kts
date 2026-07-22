dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-auth"))
    implementation(project(":platform-knowledge"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.3")
}
