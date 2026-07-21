plugins {
    application
    java
    id("com.diffplug.spotless") version "7.0.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("org.apache.kafka:kafka-clients:3.9.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets.main {
    java.srcDir("java-producer")
}

spotless {
    java {
        googleJavaFormat("1.25.2")
        target("java-producer/**/*.java")
    }
}

application {
    mainClass = "com.openeip.spike.KafkaProducerService"
}
