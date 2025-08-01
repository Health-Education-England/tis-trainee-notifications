/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.notifications.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.MongoDatabase;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class SchedulerConfigurationTest {

  private SchedulerConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new SchedulerConfiguration();
  }

  @Test
  void shouldCreateMongoLockProvider() {
    MongoTemplate template = mock(MongoTemplate.class);
    MongoDatabase database = mock(MongoDatabase.class);
    when(template.getDb()).thenReturn(database);

    LockProvider lockProvider = configuration.lockProvider(template);

    assertThat("Unexpected lock provider type.", lockProvider, instanceOf(MongoLockProvider.class));
  }

  @Test
  void shouldUseShedLockMongoDatabase() {
    MongoTemplate template = mock(MongoTemplate.class);
    MongoDatabase database = mock(MongoDatabase.class);
    when(template.getDb()).thenReturn(database);

    configuration.lockProvider(template);

    verify(database).getCollection("shedLock");
  }
}
