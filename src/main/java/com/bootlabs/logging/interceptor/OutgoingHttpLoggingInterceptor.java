package com.bootlabs.logging.interceptor;

import com.bootlabs.logging.config.LoggingProperties;
import com.bootlabs.logging.util.HeaderMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.logstash.logback.marker.Markers.appendEntries;

/**
 * {@link ClientHttpRequestInterceptor} that logs every outgoing HTTP call made
 * via {@link org.springframework.web.client.RestClient}.
 *
 * <p>The response body stream is fully buffered here so it remains readable by
 * the caller after this interceptor returns.
 *
 * <p>MDC-stored {@code traceId} / {@code correlationId} are forwarded as
 * {@code X-Trace-Id} / {@code X-Correlation-Id} headers on the outgoing request,
 * enabling end-to-end trace propagation to downstream services.
 *
 * <p>Body logging is controlled by {@code logging.http.log-body} (default {@code false}).
 */
@Component
public class OutgoingHttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OutgoingHttpLoggingInterceptor.class);

    private static final Set<String> LOGGABLE_CONTENT_TYPES = Set.of(
            "application/json",
            "application/xml",
            "application/x-www-form-urlencoded"
    );

    private final LoggingProperties props;

    public OutgoingHttpLoggingInterceptor(LoggingProperties props) {
        this.props = props;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        // ── Propagate trace context to downstream service ─────────────────
        String traceId       = MDC.get("traceId");
        String correlationId = MDC.get("correlationId");
        if (traceId       != null) request.getHeaders().set("X-Trace-Id",       traceId);
        if (correlationId != null) request.getHeaders().set("X-Correlation-Id", correlationId);

        long startTime = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long duration = System.currentTimeMillis() - startTime;

        // Buffer the entire response body so the caller can still read it.
        byte[] responseBody = response.getBody().readAllBytes();
        var buffered = new BufferedClientHttpResponse(response, responseBody);

        logOutgoing(request, body, buffered, duration);

        return buffered;
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private void logOutgoing(HttpRequest                request,
                              byte[]                    requestBody,
                              BufferedClientHttpResponse response,
                              long                       duration) throws IOException {

        URI    uri    = request.getURI();
        String method = request.getMethod().name();
        int    status = response.getStatusCode().value();

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event.kind",       "outbound");
        fields.put("http.method",      method);
        // http.url carries the path; http.target carries the full URL
        fields.put("http.url",         uri.getPath());
        fields.put("http.query",       uri.getRawQuery() != null ? uri.getRawQuery() : "");
        fields.put("http.target",      uri.toString());
        fields.put("http.status_code", status);
        fields.put("event.duration",   duration);

        // Request headers (masked, normalised to lowercase)
        request.getHeaders().forEach((name, values) -> {
            String key   = name.toLowerCase(Locale.ROOT);
            String value = String.join(", ", values);
            fields.put("http.request.headers." + key,
                    HeaderMaskingUtil.mask(Map.of(key, value)).get(key));
        });

        // Response headers (masked, normalised to lowercase)
        response.getHeaders().forEach((name, values) -> {
            String key   = name.toLowerCase(Locale.ROOT);
            String value = String.join(", ", values);
            fields.put("http.response.headers." + key,
                    HeaderMaskingUtil.mask(Map.of(key, value)).get(key));
        });

        // Bodies — included only when logging.http.log-body=true
        if (props.isLogBody()) {
            addRequestBody(requestBody,  request.getHeaders().getContentType(),  fields);
            addResponseBody(response.cachedBody(), response.getHeaders().getContentType(), fields);
        }

        String message = String.format("HTTP OUT %s %s -> %d (%dms)", method, uri, status, duration);
        log.info(appendEntries(fields), message);
    }

    private void addRequestBody(byte[] body, MediaType contentType, Map<String, Object> fields) {
        if (body == null || body.length == 0 || !isLoggableContentType(contentType)) return;
        fields.put("http.request.body", truncate(new String(body, StandardCharsets.UTF_8)));
    }

    private void addResponseBody(byte[] body, MediaType contentType, Map<String, Object> fields) {
        if (body == null || body.length == 0 || !isLoggableContentType(contentType)) return;
        fields.put("http.response.body", truncate(new String(body, StandardCharsets.UTF_8)));
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private boolean isLoggableContentType(MediaType contentType) {
        if (contentType == null) return false;
        String lower = contentType.toString().toLowerCase(Locale.ROOT);
        return lower.startsWith("text/")
                || LOGGABLE_CONTENT_TYPES.stream().anyMatch(lower::contains);
    }

    private String truncate(String s) {
        int max = props.getMaxBodySize();
        return s.length() <= max ? s : s.substring(0, max) + "...[truncated]";
    }

    // ── Buffered response ─────────────────────────────────────────────────────

    /**
     * Wraps a {@link ClientHttpResponse} whose body has already been fully read
     * into {@code cachedBody}. Subsequent calls to {@link #getBody()} return a
     * fresh {@link ByteArrayInputStream} over those bytes, so the response body
     * is readable any number of times by the caller.
     */
    private record BufferedClientHttpResponse(ClientHttpResponse delegate,
                                               byte[]             cachedBody)
            implements ClientHttpResponse {

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(cachedBody);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
