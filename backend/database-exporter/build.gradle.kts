plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core")
    runtimeOnly("org.postgresql:postgresql")
}

application {
    mainClass = "space.grayt.teremok.exporter.DatabaseExporterApplication"
}
