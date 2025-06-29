plugins {
    id("java")
    id ("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.0"
}

group = "com.mumeinosato"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("net.dv8tion:JDA:5.6.1")
}

tasks.test {
    useJUnitPlatform()
}