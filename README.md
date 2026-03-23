# Centralized Logging: Spring Boot Meets Grafana, Alloy, and Loki

## Tech Stack

**Spring-boot**, **maven**, **java21**, **Docker**, **H2**, **Grafana**, **Loki**, **Alloy**

## Run Locally

Clone the project

```bash
  git clone https://github.com/anicetkeric/spring-boot-logging-grafana-alloy-loki/
```

## Documentation

[Blog Post](https://blog.boottechsolutions.com/2026/03/23/centralized-logging-spring-boot-meets-grafana-alloy-and-loki/)



## Example Log Output

### Inbound request (INFO, log-body: false)

```json
{
  "@timestamp": "2026-03-21T21:27:19.571043105+01:00",
  "@version": "1",
  "message": "HTTP POST /api/books -> 201 (42ms)",
  "logger": "com.bootlabs.logging.filter.HttpLoggingFilter",
  "thread": "http-nio-8080-exec-3",
  "level": "INFO",
  "application.name": "spring-boot-logging-grafana-alloy-loki",
  "application.version": "0.0.1-SNAPSHOT",
  "environment": "dev",
  "traceId": "a3f7b2c1-9e4d-4f8a-b123-56789abcdef0",
  "correlationId": "9d1e4f82-1234-5678-abcd-ef0123456789",
  "event.kind": "inbound",
  "http.method": "POST",
  "http.url": "/api/books",
  "http.query": "",
  "http.status_code": 201,
  "event.duration": 42,
  "client.ip": "127.0.0.1",
  "http.request.headers.content-type": "application/json",
  "http.request.headers.authorization": "***",
  "http.request.headers.accept": "*/*",
  "http.response.headers.content-type": "application/json",
  "http.response.headers.x-trace-id": "a3f7b2c1-9e4d-4f8a-b123-56789abcdef0",
  "http.response.headers.x-correlation-id": "9d1e4f82-1234-5678-abcd-ef0123456789"
}
```

### Inbound request (INFO, log-body: true)

Same as above, plus:

```json
{
  "http.request.body":  "{\"title\":\"Clean Code\",\"isbn\":\"978-0-13-235088-4\",\"authorId\":1}",
  "http.response.body": "{\"id\":11,\"title\":\"Clean Code\",\"isbn\":\"978-0-13-235088-4\"}"
}
```

### Outbound RestClient call

```json
{
  "@timestamp": "2026-03-21T21:27:19.612084721+01:00",
  "@version": "1",
  "message": "HTTP OUT GET https://api.example.com/covers/978-0-13-235088-4 -> 200 (15ms)",
  "logger": "com.bootlabs.logging.interceptor.OutgoingHttpLoggingInterceptor",
  "level": "INFO",
  "application.name": "spring-boot-logging-grafana-alloy-loki",
  "environment": "dev",
  "traceId": "a3f7b2c1-9e4d-4f8a-b123-56789abcdef0",
  "correlationId": "9d1e4f82-1234-5678-abcd-ef0123456789",
  "event.kind": "outbound",
  "http.method": "GET",
  "http.url": "/covers/978-0-13-235088-4",
  "http.target": "https://api.example.com/covers/978-0-13-235088-4",
  "http.status_code": 200,
  "event.duration": 15,
  "http.request.headers.x-trace-id": "a3f7b2c1-9e4d-4f8a-b123-56789abcdef0",
  "http.response.headers.content-type": "application/json"
}
```

Notice both the inbound and outbound log lines carry the same `traceId` — you can reconstruct the full request lifecycle from a single ID.

### Error with stack trace

```json
{
  "@timestamp": "2026-03-21T21:27:55.003142890+01:00",
  "message": "HTTP GET /api/books/999 -> 500 (8ms)",
  "level": "ERROR",
  "traceId": "f9a2b3c4-...",
  "event.kind": "inbound",
  "http.status_code": 500,
  "error.stack_trace": "java.util.NoSuchElementException: No value present\n\tat java.base/java.util.Optional.get(Optional.java:143)\n\t..."
}
```






## Authors

👤 **anicetkeric**

* Website: https://medium.com/@boottechnologies-ci
* Twitter: [@AnicetKEric](https://twitter.com/AnicetKEric)
* Github: [@anicetkeric](https://github.com/anicetkeric)
* LinkedIn: [@eric-anicet-kouame](https://linkedin.com/in/eric-anicet-kouame-49029577)

## Support
<a href="https://www.buymeacoffee.com/boottechnou" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174"></a>
