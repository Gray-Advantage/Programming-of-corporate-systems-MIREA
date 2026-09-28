plugins {
    application
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation(project(":backend-client-shared"))
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.postgresql:postgresql:42.7.13")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.h2database:h2:2.5.252")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "space.grayt.teremok.Main"
}

// A console app needs a live stdin, otherwise ./gradlew run is useless.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

// Tests build their H2 databases from the same schema.sql and seed.sql that PostgreSQL loads.
tasks.processTestResources {
    from(rootProject.file("database")) {
        into("database")
    }
}
