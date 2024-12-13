/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.repository;

import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MYSQL;

import io.awspring.cloud.s3.S3Template;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.config.MongoCollectionConfiguration;
import uk.nhs.tis.trainee.notifications.service.ApplicationPropertiesService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationsTest implements TestExecutionListener {

  @Container
  @ServiceConnection
  private static final MySQLContainer<?> mySqlContainer = new MySQLContainer<>(MYSQL);

  @DynamicPropertySource
  private static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.flyway.url", mySqlContainer::getJdbcUrl);
    registry.add("spring.flyway.schemas", mySqlContainer::getDatabaseName);
    registry.add("spring.flyway.user", mySqlContainer::getUsername);
    registry.add("spring.flyway.password", mySqlContainer::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
  }

  @MockBean
  private MongoCollectionConfiguration mongoConfiguration;

  @MockBean
  private ApplicationPropertiesService appPropService;

  @MockBean
  private S3Template s3Template;

  @Autowired
  Flyway flyway;

  @Override
  public void beforeTestMethod(TestContext testContext) {
    flyway.clean();
    flyway.migrate();
  }

  @Test
  void validateFlywayMigrations() {
    flyway.validate();
  }
}
