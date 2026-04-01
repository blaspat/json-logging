JSON Logging
===========================

A Spring Boot auto-config library that logs HTTP request/response pairs as structured JSON — ready for ELK, Datadog, or any JSON-aware log aggregator.

## Key Features

- **Structured JSON logs** — every request/response is a single JSON object with consistent fields (`type`, `method`, `uri`, `timestamp`, `correlationId`, `status`, `elapsedMs`, `body`)
- **Correlation ID** — auto-generated or pulled from `x-correlation-id` / `x-request-id` headers
- **Sensitive data masking** — passwords, tokens, API keys and other known sensitive params are automatically redacted
- **Excludable paths** — configure URI patterns to skip (e.g. `/actuator/**`)
- **Auto-configuration** — enable with a single property; no `@ComponentScan` pollution
- **ELK-ready output** — `JsonLayout` wraps your logs with `severity`, `logType`, `correlationId`, and stack traces on ERROR

Requires **Java 8** and **Spring Boot 2.x**.

## Maven

```xml
<dependency>
    <groupId>io.github.blaspat</groupId>
    <artifactId>json-logging</artifactId>
    <version>1.0.1</version>
</dependency>
```

## Quick Start

1. Add the dependency above.
2. Configure `logback-spring.xml` (see example below).
3. Set `logging.enabled=true` in `application.yml` (default is `true`).

```yaml
logging:
  enabled: true
  excluded-paths: /actuator/**,/health
  level:
    root: INFO
    io.github.blaspat: DEBUG
```

### logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <appender name="JSON" class="io.github.blaspat.logback.JsonAppender">
        <encoder>
            <layout class="io.github.blaspat.logback.JsonLayout"/>
        </encoder>
    </appender>

    <logger name="com.example.yourapp" level="DEBUG" additivity="false">
        <appender-ref ref="JSON"/>
    </logger>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

## Configuration

| Property | Type | Default | Description |
|---|---|---|---|
| `logging.enabled` | Boolean | `true` | Enable/disable auto-configuration |
| `logging.excluded-paths` | String | _(none)_ | Comma-separated Ant-style URI patterns to exclude |
| `logging.masking.enabled` | Boolean | `true` | Enable sensitive-value redaction in JSON output |

## Log Output Format

**Request:**
```json
{
  "type": "REQUEST",
  "method": "POST",
  "uri": "/api/users",
  "timestamp": "2025-01-15T10:30:00.123Z",
  "correlationId": "abc-123",
  "userAgent": "Mozilla/5.0 ...",
  "clientIp": "192.168.1.1",
  "queryParams": { "page": "1", "size": "20" },
  "body": { "name": "Alice", "age": 30 }
}
```

**Response:**
```json
{
  "type": "RESPONSE",
  "method": "POST",
  "uri": "/api/users",
  "timestamp": "2025-01-15T10:30:00.456Z",
  "correlationId": "abc-123",
  "status": 201,
  "elapsedMs": 42,
  "body": { "id": 42, "name": "Alice" }
}
```

## Changelog

### 1.0.1
- **Breaking:** Removed Gson dependency — Jackson only
- **Breaking:** Log output is now proper JSON (was tab-separated strings)
- **Fix:** Removed `@ComponentScan` — now uses proper Spring auto-configuration
- **Fix:** Sensitive query parameters are automatically redacted
- **Fix:** MDC keys are now fixed (`correlationId`, `userAgent`, `clientIp`) instead of raw header names
- **Fix:** Consistent UTC timestamps across all log output
- **Fix:** Replaced deprecated `MediaType.APPLICATION_JSON_UTF8`
- **Upgrade:** Spring Boot 2.5.x → 2.7.18
- **Upgrade:** Replaced unmaintained `logback-contrib` with actively-maintained `logstash-logback-encoder`
- **Upgrade:** Modernized `spring.factories` → `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- **Added:** `logback-spring-example.xml` documentation and property metadata
- **Added:** Unit tests with Spring Boot Test

### 1.0.0
- Initial release

## License

Apache License 2.0 — Blasius Patrick
