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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.config.EcsMetadataConfiguration.EcsMetadata.ContainerMetadata;
import uk.nhs.tis.trainee.notifications.config.EcsMetadataConfiguration.EcsMetadata.TaskMetadata;

/**
 * Configuration for retrieval of ECS metadata.
 */
@Configuration
@ConditionalOnExpression("!T(org.springframework.util.StringUtils)"
    + ".isEmpty('${ecs.container.metadata.uri.v4:}')")
public class EcsMetadataConfiguration {

  /**
   * Generate ECS metadata based on the ECS metadata endpoint.
   *
   * @param restTemplate     The rest template to call the endpoint with.
   * @param metadataEndpoint The endpoint to call to get ECS metadata.
   * @return The parsed ECS metadata.
   */
  @Bean
  public EcsMetadata ecsMetadata(RestTemplate restTemplate,
      @Value("${ecs.container.metadata.uri.v4}") String metadataEndpoint) {
    ContainerMetadata containerMetadata = restTemplate.getForObject(metadataEndpoint,
        ContainerMetadata.class);
    TaskMetadata taskMetadata = restTemplate.getForObject(metadataEndpoint + "/task",
        TaskMetadata.class);

    return new EcsMetadata(taskMetadata, containerMetadata);
  }

  /**
   * A representation of ECS metadata.
   *
   * @param taskMetadata      The ECS task metadata.
   * @param containerMetadata The ECS container metadata.
   */
  public record EcsMetadata(
      @JsonProperty("TaskMetadata")
      TaskMetadata taskMetadata,

      @JsonProperty("ContainerMetadata")
      ContainerMetadata containerMetadata) {

    /**
     * A representation of ECS task metadata.
     *
     * @param cluster  The ECS cluster.
     * @param taskArn  The running task's ARN.
     * @param family   The task definition family.
     * @param revision The task definition revision.
     */
    record TaskMetadata(
        @JsonProperty("Cluster")
        String cluster,

        @JsonProperty("TaskARN")
        String taskArn,

        @JsonProperty("Family")
        String family,

        @JsonProperty("Revision")
        String revision) {

    }

    /**
     * A representation of ECS container metadata.
     *
     * @param containerArn The ECS container ARN.
     * @param logOptions   The container's log options.
     */
    record ContainerMetadata(

        @JsonProperty("ContainerARN")
        String containerArn,

        @JsonProperty("LogOptions")
        LogOptions logOptions) {

      /**
       * A representation of ECS container log options.
       *
       * @param logGroup  The container's log group.
       * @param region    The container's log region.
       * @param logStream The container's current log stream.
       */
      record LogOptions(
          @JsonProperty("awslogs-group")
          String logGroup,

          @JsonProperty("awslogs-region")
          String region,

          @JsonProperty("awslogs-stream")
          String logStream) {

      }
    }
  }
}
