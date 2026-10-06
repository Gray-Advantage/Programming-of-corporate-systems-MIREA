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
    implementation(project(":backend:backend-shared:jwt-auth"))
    implementation(project(":backend:backend-shared:object-storage"))
    implementation(project(":backend-client-shared"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.bootJar {
    archiveFileName = "draft-recordings-service.jar"
}

tasks.test {
    useJUnitPlatform()
}
