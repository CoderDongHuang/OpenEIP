import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.github.spotbugs") version "6.0.26" apply false
    id("com.diffplug.spotless") version "7.0.2" apply false
}

group = "com.openeip"
version = "0.1.0-alpha"

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "checkstyle")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "jacoco")

    group = "com.openeip"
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        providers.gradleProperty("mavenRepositoryUrl").orNull?.let {
            maven(url = it)
        }
        mavenCentral()
    }

    dependencies {
        add("testImplementation", "org.springframework.boot:spring-boot-starter-test:3.4.0")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.20.2"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    }

    extensions.configure<SpotlessExtension> {
        java {
            googleJavaFormat()
            removeUnusedImports()
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy("jacocoTestReport")
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            xml.required = true
            html.required = true
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) {
                    exclude("**/*Application.class")
                }
            })
        )
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn("spotbugsMain", "spotlessCheck", "jacocoTestCoverageVerification")
    }
}
