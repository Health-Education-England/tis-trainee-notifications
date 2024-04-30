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

package uk.nhs.tis.trainee.notifications.config;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import uk.nhs.tis.trainee.notifications.model.History;

class MongoConfigurationTest {

  private MongoConfiguration configuration;

  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    configuration = new MongoConfiguration(template);
  }

  @Test
  void shouldInitIndexesForNotificationHistoryCollection() {
    IndexOperations indexOperations = mock(IndexOperations.class);
    when(template.indexOps(History.class)).thenReturn(indexOperations);

    configuration.initIndexes();

    ArgumentCaptor<IndexDefinition> indexCaptor = ArgumentCaptor.captor();
    verify(indexOperations, atLeastOnce()).ensureIndex(indexCaptor.capture());

    List<IndexDefinition> indexes = indexCaptor.getAllValues();
    assertThat("Unexpected number of indexes.", indexes.size(), is(1));

    List<String> indexKeys = indexes.stream()
        .flatMap(i -> i.getIndexKeys().keySet().stream())
        .collect(Collectors.toList());
    assertThat("Unexpected number of index keys.", indexKeys.size(), is(1));
    assertThat("Unexpected index.", indexKeys, hasItems("recipient.id"));
  }
}
