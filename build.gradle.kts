plugins {
    id("java")
    id ("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.0"
}

group = "com.mumeinosato"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://m2.dv8tion.net/releases")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("net.dv8tion:JDA:5.6.1")

    // Spring Boot starterからlogbackを除外してlog4j2を使用
    implementation("org.springframework.boot:spring-boot-starter") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    implementation("org.springframework.boot:spring-boot-starter-log4j2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    implementation("com.sedmelluq:lavaplayer:1.3.77")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// bootRun タスクの設定を追加
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // シャットダウンの猶予期間を設定
    systemProperty("spring.lifecycle.timeout-per-shutdown-phase", "30s")

    // JVMのシャットダウンフックを有効にする
    jvmArgs("-Dspring.main.registerShutdownHook=true")
}
