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
    implementation(project(":backend:backend-shared:kafka-events"))
    implementation("org.apache.kafka:kafka-clients")
    implementation("tools.jackson.core:jackson-databind")
    runtimeOnly("org.slf4j:slf4j-simple")
}

application {
    mainClass = "space.grayt.teremok.deployer.TextWorkDeployerApplication"
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn"
    )
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
