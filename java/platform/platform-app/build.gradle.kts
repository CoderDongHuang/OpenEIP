plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-auth"))
    implementation(project(":platform-document"))
    implementation(project(":platform-knowledge"))
    implementation(project(":platform-chat"))
    implementation(project(":platform-agent"))
    implementation(project(":platform-workflow"))
    implementation(project(":platform-connector"))
    implementation(project(":platform-governance"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("com.h2database:h2")
}
