# TIS Trainee Notifications

## About
This service manages and sends notifications to trainees based on TIS
Self-Service events.

## Developing

### Running

```shell
gradlew bootRun
```

#### Pre-Requisites

 - A Redis instance for caching Person ID to User ID mappings.
 - A running instance of localstack or access to Amazon SQS, to retrieve
   messages from the queue given as `COJ_RECEIVED_QUEUE`.
 - `COJ_RECEIVED_QUEUE` should have a visibility timeout longer than the user
   cache takes to build, failure to do so may lead to duplicate emails being
   sent. See log message `Total time taken to cache all user accounts was:
   <num>s` for time taken.
 - Access to an Amazon Cognito User Pool to get user details.

##### AWS Credentials

AWS credentials are retrieved using the [DefaultCredentialsProvider], see the
associated documentation for details of how to provide credentials.

##### AWS Region

The AWS region is retrieved using the [DefaultAwsRegionProviderChain], see the
associated document for details of how to provide the region.

#### Environmental Variables

| Name                          | Description                                                        | Default   |
|-------------------------------|--------------------------------------------------------------------|-----------|
| ACCOUNT_CONFIRMED_QUEUE       | The queue URL for account confirmation events.                     |           |
| APP_DOMAIN                    | The domain to be used for links in email notifications. (Optional) |           |
| AWS_XRAY_DAEMON_ADDRESS       | The AWS XRay daemon host. (Optional)                               |           |
| COGNITO_USER_POOL_ID          | The user pool to get user details from.                            |           |
| COJ_RECEIVED_QUEUE            | The queue URL for Conditions of Joining received events.           |           |
| ENVIRONMENT                   | The environment to log events against.                             | local     |
| EMAIL_SENDER                  | Where email notifications are to be sent from.                     |           |
| NOTIFICATIONS_EVENT_TOPIC_ARN | Broadcast endpoint for notification events                         |           |
| REDIS_HOST                    | Redis server host                                                  | localhost |
| REDIS_PASSWORD                | Login password of the redis server.                                | password  |
| REDIS_PORT                    | Redis server port.                                                 | 6379      |
| REDIS_SSL                     | Whether to enable SSL support.                                     | false     |
| REDIS_USERNAME                | Login username of the redis server                                 | default   |
| SENTRY_DSN                    | A Sentry error monitoring Data Source Name. (Optional)             |           |

#### Usage Examples

##### Conditions of Joining Received

The Conditions of Joining event should be sent to the `COJ_RECEIVED_QUEUE`, with
the following structure.

```json
{
  "personId": "47165",
  "conditionsOfJoining": {
    "syncedAt": "2022-08-01T22:01:02Z"
  }
}
```

#### Service Health

Spring Actuator is included to provide a health check endpoint, which  can be
accessed at `<host>:<port>/notifications/actuator/health`.

### Testing

The Gradle `test` task can be used to run automated tests and produce coverage
reports.
```shell
gradlew test
```

The Gradle `check` lifecycle task can be used to run automated tests and also
verify formatting conforms to the code style guidelines.
```shell
gradlew check
```

### Building

```shell
gradlew bootBuildImage
```

## Versioning
This project uses [Semantic Versioning](semver.org).

## License
This project is license under [The MIT License (MIT)](LICENSE).

[DefaultCredentialsProvider]:(https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/DefaultCredentialsProvider.html)
[DefaultAwsRegionProviderChain]:(https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/regions/providers/DefaultAwsRegionProviderChain.html)
