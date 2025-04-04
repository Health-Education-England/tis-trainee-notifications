rootProject.name = "tis-trainee-notifications"

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }

  versionCatalogs {
    create("libs") {
      from("uk.nhs.tis.trainee:version-catalog:0.0.5")
    }
  }
}
