plugins {
    application
    java
    id("com.google.protobuf") version "0.9.5"
    id("com.diffplug.spotless") version "7.0.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.google.protobuf:protobuf-java:4.30.2")
    implementation("io.grpc:grpc-netty-shaded:1.71.0")
    implementation("io.grpc:grpc-protobuf:1.71.0")
    implementation("io.grpc:grpc-stub:1.71.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    main {
        java.srcDir("java-client")
        proto.srcDir("proto")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.30.2"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.71.0"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
            }
        }
    }
}

spotless {
    java {
        googleJavaFormat("1.25.2")
        target("java-client/**/*.java")
    }
}

application {
    mainClass = "com.openeip.spike.GrpcClient"
}
