package com.flowable.designbackup.migration;

import java.util.regex.Pattern;

/**
 * Find/replace rules for migrating Jackson 2 references (left over from
 * Flowable 2025.1.x / Spring Boot 3 model content) to Jackson 3 (Flowable
 * 2026.1.5 / Spring Boot 4).
 *
 * <p>Verified against the actual Jackson 3 jar (tools.jackson:jackson-databind):
 * the common {@code JsonNode}/{@code ObjectMapper} accessor methods (
 * {@code asText}, {@code asBoolean}, {@code textValue}, ...) kept their
 * names — there is no method-level rename to apply there. The two real
 * changes are the package rename, and {@code JsonProcessingException} being
 * removed in favor of the unchecked {@code JacksonException}. Both are
 * plain, safe text substitutions; everything else (e.g. a
 * {@code catch (IOException e)} around Jackson calls, which no longer
 * catches anything since Jackson 3 exceptions are unchecked) is flagged for
 * manual review rather than rewritten, since fixing it correctly requires
 * understanding the surrounding code.
 */
public final class JacksonMigrationRules {

    private static final String OLD_PACKAGE_PREFIX = "com.fasterxml.jackson.";
    private static final String NEW_PACKAGE_PREFIX = "tools.jackson.";
    private static final String OLD_JSON_PROCESSING_EXCEPTION = "com.fasterxml.jackson.core.JsonProcessingException";
    private static final String NEW_JACKSON_EXCEPTION = "tools.jackson.core.JacksonException";
    private static final Pattern IO_EXCEPTION_HINT = Pattern.compile("\\bIOException\\b");

    private JacksonMigrationRules() {
    }

    /**
     * @return how many old-package references {@code content} has, and
     *         whether it also mentions {@code IOException} (a sign that
     *         exception handling around Jackson calls needs a human look,
     *         since Jackson 3 throws an unchecked exception instead).
     */
    public static ScanResult scan(String content) {
        if (content == null || content.isEmpty()) {
            return new ScanResult(0, false);
        }
        int occurrences = countOccurrences(content, OLD_PACKAGE_PREFIX);
        boolean needsManualReview = occurrences > 0 && IO_EXCEPTION_HINT.matcher(content).find();
        return new ScanResult(occurrences, needsManualReview);
    }

    /**
     * Applies the safe, mechanical renames: {@code JsonProcessingException}
     * first (a class rename, not just a package move), then the generic
     * package prefix for everything else.
     */
    public static FixResult fix(String content) {
        if (content == null || content.isEmpty()) {
            return new FixResult(content, 0);
        }
        int occurrences = countOccurrences(content, OLD_PACKAGE_PREFIX);
        String fixed = content
                .replace(OLD_JSON_PROCESSING_EXCEPTION, NEW_JACKSON_EXCEPTION)
                .replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
        return new FixResult(fixed, occurrences);
    }

    private static int countOccurrences(String content, String needle) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    public record ScanResult(int occurrences, boolean needsManualReview) {
    }

    public record FixResult(String fixedContent, int occurrences) {
    }
}
