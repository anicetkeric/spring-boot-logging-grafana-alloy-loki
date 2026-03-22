package com.bootlabs.logging.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Replaces values of known sensitive HTTP headers with {@code ***}.
 *
 * <p>Header name comparison is case-insensitive. The returned map preserves
 * insertion order and uses the already-normalised (lowercase) key supplied by
 * the caller.
 */
public final class HeaderMaskingUtil {

    public static final String MASKED_VALUE = "***";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie"
    );

    private HeaderMaskingUtil() {
    }

    /**
     * Returns a new map in which values for sensitive headers are replaced with
     * {@value #MASKED_VALUE}. The input map must already use lowercase header
     * names (as produced by {@link com.bootlabs.logging.filter.HttpLoggingFilter}).
     *
     * @param headers normalised (lowercase) header name → value map
     * @return masked copy of the input map
     */
    public static Map<String, String> mask(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>(headers.size());
        headers.forEach((name, value) ->
                result.put(name, isSensitive(name) ? MASKED_VALUE : value));
        return result;
    }

    private static boolean isSensitive(String lowercaseName) {
        return SENSITIVE_HEADERS.contains(lowercaseName.toLowerCase(Locale.ROOT));
    }
}
