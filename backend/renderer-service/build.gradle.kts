plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation(project(":backend:backend-shared:kafka-events"))
    implementation(project(":backend:backend-shared:object-storage"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.bootJar {
    archiveFileName = "renderer-service.jar"
}

tasks.test {
    useJUnitPlatform()
}
