package com.bootlabs.logging.filter;

import com.bootlabs.logging.config.LoggingProperties;
import com.bootlabs.logging.util.HeaderMaskingUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.marker.Markers.appendEntries;

/**
 * Servlet filter that logs every HTTP request/response cycle as a single
 * structured JSON log line.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Single log entry per exchange</b> – logged <em>after</em> the
 *       filter chain so that request-body bytes are available (Spring's
 *       {@link ContentCachingRequestWrapper} buffers lazily as the downstream
 *       servlet reads the stream).  Response body is available for the same
 *       reason.</li>
 *   <li><b>Stream safety</b> – both wrappers cache bytes internally while
 *       still serving the original streams.
 *       {@link ContentCachingResponseWrapper#copyBodyToResponse()} is called
 *       in {@code finally} to flush the cached bytes back to the real
 *       response before the connection is closed.</li>
 *   <li><b>Body logging at DEBUG only</b> – a separate, second log event is
 *       emitted only when the effective log level is DEBUG, keeping INFO logs
 *       lean.</li>
 *   <li><b>Tracing</b> – {@code X-Trace-Id} / {@code X-Correlation-Id} are
 *       extracted from inbound headers (or generated) and stored in MDC so
 *       that every log line emitted during the request (not just this filter)
 *       carries them automatically.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    // Content-types whose bodies are safe to log as text.
    private static final Set<String> LOGGABLE_CONTENT_TYPES = Set.of(
            "application/json",
            "application/xml",
            "application/x-www-form-urlencoded"
    );

    private static final String MDC_TRACE_ID       = "traceId";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final LoggingProperties props;

    public HttpLoggingFilter(LoggingProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return props.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Wrap both streams so bodies can be read multiple times.
        // The constructor's second arg caps the request-body cache size.
        var wrappedRequest  = new ContentCachingRequestWrapper(request,  props.getMaxBodySize());
        var wrappedResponse = new ContentCachingResponseWrapper(response);

        // ── Trace / correlation IDs ───────────────────────────────────────
        String traceId = Optional.ofNullable(request.getHeader("X-Trace-Id"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        MDC.put(MDC_TRACE_ID,       traceId);
        MDC.put(MDC_CORRELATION_ID, correlationId);

        // Propagate IDs back to the caller.
        wrappedResponse.addHeader("X-Trace-Id",       traceId);
        wrappedResponse.addHeader("X-Correlation-Id", correlationId);

        // ── Execute chain ─────────────────────────────────────────────────
        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Log BEFORE copyBodyToResponse so the buffer is still intact.
            logExchange(wrappedRequest, wrappedResponse, duration);

            // Flush cached response body to the actual output stream.
            wrappedResponse.copyBodyToResponse();

            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    // ── Logging helpers ───────────────────────────────────────────────────────

    /**
     * Emits one INFO log event containing all required fields.
     * Bodies are added to the same event when {@code logging.http.log-body=true}.
     */
    private void logExchange(ContentCachingRequestWrapper  request,
                              ContentCachingResponseWrapper response,
                              long                          duration) {

        String method   = request.getMethod();
        String uri      = request.getRequestURI();
        String query    = request.getQueryString();
        int    status   = response.getStatus();
        String clientIp = resolveClientIp(request);

        // Build a flat map; keys with dots become flat JSON fields (not nested)
        // when serialised by logstash-logback-encoder's appendEntries().
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event.kind",       "inbound");
        fields.put("http.method",      method);
        fields.put("http.url",         uri);
        fields.put("http.query",       query != null ? query : "");
        fields.put("http.status_code", status);
        fields.put("event.duration",   duration);   // milliseconds
        fields.put("client.ip",        clientIp);

        // Request headers (masked, normalised to lowercase)
        extractRequestHeaders(request)
                .forEach((k, v) -> fields.put("http.request.headers."  + k, v));

        // Response headers (masked, normalised to lowercase)
        extractResponseHeaders(response)
                .forEach((k, v) -> fields.put("http.response.headers." + k, v));

        // Bodies — included only when logging.http.log-body=true
        if (props.isLogBody()) {
            addBodies(request, response, fields);
        }

        String message = String.format("HTTP %s %s -> %d (%dms)", method, uri, status, duration);
        log.info(appendEntries(fields), message);
    }

    /**
     * Mutates {@code fields} by adding {@code http.request.body} and/or
     * {@code http.response.body} for loggable content-types with non-empty bodies.
     */
    private void addBodies(ContentCachingRequestWrapper  request,
                            ContentCachingResponseWrapper response,
                            Map<String, Object>           fields) {

        if (isLoggableContentType(request.getContentType())) {
            byte[] bytes = request.getContentAsByteArray();
            if (bytes.length > 0) {
                fields.put("http.request.body", truncate(new String(bytes, StandardCharsets.UTF_8)));
            }
        }

        if (isLoggableContentType(response.getContentType())) {
            byte[] bytes = response.getContentAsByteArray();
            if (bytes.length > 0) {
                fields.put("http.response.body", truncate(new String(bytes, StandardCharsets.UTF_8)));
            }
        }
    }

    // ── Header extraction ─────────────────────────────────────────────────────

    private Map<String, String> extractRequestHeaders(HttpServletRequest request) {
        Map<String, String>      raw   = new LinkedHashMap<>();
        Enumeration<String>      names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                // First value only; multi-value headers are uncommon for logging
                raw.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
            }
        }
        return HeaderMaskingUtil.mask(raw);
    }

    private Map<String, String> extractResponseHeaders(HttpServletResponse response) {
        Map<String, String> raw = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            raw.put(name.toLowerCase(Locale.ROOT), response.getHeader(name));
        }
        return HeaderMaskingUtil.mask(raw);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} for text-based content types whose bodies are safe
     * to read and log. Excludes multipart, octet-stream, and other binary types.
     */
    private boolean isLoggableContentType(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("text/")
                || LOGGABLE_CONTENT_TYPES.stream().anyMatch(lower::contains);
    }

    /** Cuts the body string at {@code maxBodySize} and appends a marker. */
    private String truncate(String body) {
        int max = props.getMaxBodySize();
        return body.length() <= max
                ? body
                : body.substring(0, max) + "...[truncated]";
    }

    /**
     * Resolves the real client IP, honouring common reverse-proxy headers.
     * {@code X-Forwarded-For} may contain a comma-separated list; the
     * left-most entry is the original client.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
