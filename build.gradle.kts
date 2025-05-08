plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)

  // Code quality plugins
  checkstyle
  jacoco
  alias(libs.plugins.sonarqube)
}

group = "uk.nhs.tis.trainee"
version = "2.5.2"

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

repositories {
  mavenCentral()
}

dependencyManagement {
  imports {
    mavenBom(libs.spring.cloud.dependencies.core.get().toString())
    mavenBom(libs.spring.cloud.dependencies.aws.get().toString())
  }
}

dependencies {
  // Spring Boot starters
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.springframework.boot:spring-boot-starter-quartz")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-web")
  testImplementation("org.springframework.boot:spring-boot-starter-test")

  implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-ses")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")
  implementation("software.amazon.awssdk:cognitoidentityprovider")
  implementation(libs.aws.xray)

  implementation("com.mysql:mysql-connector-j")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-mysql")

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  // MapStruct
  implementation(libs.mapstruct.core)
  annotationProcessor(libs.mapstruct.processor)

  // Mongock
  implementation(libs.bundles.mongock)

  // Sentry reporting
  implementation(libs.bundles.sentry)

  testImplementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
  testImplementation("com.playtika.testcontainers:embedded-redis:3.1.5")
  testImplementation("com.h2database:h2")

  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.testcontainers:localstack")
  testImplementation("org.testcontainers:mongodb")
  testImplementation("org.testcontainers:mysql")
  testImplementation("org.awaitility:awaitility")

  testImplementation(libs.jsoup)
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
    vendor.set(JvmVendorSpec.ADOPTIUM)
  }
}

checkstyle {
  config = resources.text.fromArchiveEntry(configurations.checkstyle.get().first(), "google_checks.xml")
}

sonarqube {
  properties {
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.login", System.getenv("SONAR_TOKEN"))
    property("sonar.organization", "health-education-england")
    property("sonar.projectKey", "Health-Education-England_tis-trainee-notifications")

    property("sonar.java.checkstyle.reportPaths",
      "build/reports/checkstyle/main.xml,build/reports/checkstyle/test.xml")
  }
}

tasks.jacocoTestReport {
  reports {
    html.required.set(true)
    xml.required.set(true)
  }
}

tasks.test {
  finalizedBy(tasks.jacocoTestReport)
  useJUnitPlatform()
}
