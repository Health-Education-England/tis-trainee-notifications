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

#### Environmental Variables

| Name                              | Description                                                                                                             | Default     |
|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------|-------------|
| AWS_XRAY_DAEMON_ADDRESS           | The AWS XRay daemon host.                                                                                               |             |
| ENVIRONMENT                       | The environment to log events against.                                                                                  | local       |
| SENTRY_DSN                        | A Sentry error monitoring Data Source Name.                                                                             |             |

#### Usage Examples

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
