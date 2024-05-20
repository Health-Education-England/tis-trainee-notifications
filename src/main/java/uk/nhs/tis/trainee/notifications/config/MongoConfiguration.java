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

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.Jsr310Converters;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Additional configuration for MongoDB.
 */
@Configuration
@Slf4j
public class MongoConfiguration {

  /**
   * Mongo template with custom converter.
   *
   * @param dbFactory The Mongo Database Factory to use.
   * @param mongoConverter The Mapping Mongo Converter to use.
   * @return the Mongo template.
   */
  @Bean
  public MongoTemplate mongoTemplate(MongoDatabaseFactory dbFactory,
      MappingMongoConverter mongoConverter) {

    List<Converter<?, ?>> converters = List.of(
        DateToObjectConverter.INSTANCE);

    MongoCustomConversions customConversions = new MongoCustomConversions(converters);
    mongoConverter.setCustomConversions(customConversions);
    mongoConverter.afterPropertiesSet();
    return new MongoTemplate(dbFactory, mongoConverter);
  }

  /**
   * Map dates to LocalDate when the target is an Object.
   */
  @ReadingConverter
  public static class DateToObjectConverter implements Converter<Date, Object> {

    public static final DateToObjectConverter INSTANCE = new DateToObjectConverter();

    /**
     * Convert a Date to an Object of type LocalDate.
     *
     * @param source the source object to convert, which must be an instance of {@code S}
     *               (never {@code null})
     * @return The LocalDate Object if it can be converted, otherwise null or the source Date.
     */
    public Object convert(Date source) {
      if (source == null) {
        return null;
      }
      try {
        long timestamp = source.getTime();
        if (Long.MIN_VALUE == timestamp) {
          return LocalDate.MIN;
        }
        if (Long.MAX_VALUE == timestamp) {
          return LocalDate.MAX;
        }
        return Jsr310Converters.DateToLocalDateConverter.INSTANCE.convert(source);
      } catch (Exception e) {
        return source;
      }
    }
  }

}
