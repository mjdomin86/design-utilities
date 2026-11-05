package com.flowable.designbackup.migration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonMigrationRulesTest {

    @Test
    void scanReportsZeroOccurrencesForCleanContent() {
        JacksonMigrationRules.ScanResult result = JacksonMigrationRules.scan("import tools.jackson.databind.ObjectMapper");

        assertThat(result.occurrences()).isZero();
        assertThat(result.needsManualReview()).isFalse();
    }

    @Test
    void scanCountsEachOldPackageReference() {
        String content = """
                import com.fasterxml.jackson.databind.ObjectMapper
                import com.fasterxml.jackson.databind.JsonNode
                def mapper = new ObjectMapper()
                """;

        JacksonMigrationRules.ScanResult result = JacksonMigrationRules.scan(content);

        assertThat(result.occurrences()).isEqualTo(2);
        assertThat(result.needsManualReview()).isFalse();
    }

    @Test
    void scanFlagsManualReviewWhenIoExceptionIsAlsoPresent() {
        String content = """
                import com.fasterxml.jackson.databind.ObjectMapper
                try {
                    mapper.readValue(json, Map.class)
                } catch (IOException e) {
                    log.error(e)
                }
                """;

        JacksonMigrationRules.ScanResult result = JacksonMigrationRules.scan(content);

        assertThat(result.needsManualReview()).isTrue();
    }

    @Test
    void scanHandlesNullAndEmptyContent() {
        assertThat(JacksonMigrationRules.scan(null).occurrences()).isZero();
        assertThat(JacksonMigrationRules.scan("").occurrences()).isZero();
    }

    @Test
    void fixRenamesThePackagePrefix() {
        JacksonMigrationRules.FixResult result = JacksonMigrationRules.fix(
                "import com.fasterxml.jackson.databind.ObjectMapper");

        assertThat(result.fixedContent()).isEqualTo("import tools.jackson.databind.ObjectMapper");
        assertThat(result.occurrences()).isEqualTo(1);
    }

    @Test
    void fixRenamesJsonProcessingExceptionToJacksonExceptionBeforeThePackagePrefix() {
        JacksonMigrationRules.FixResult result = JacksonMigrationRules.fix(
                "catch (com.fasterxml.jackson.core.JsonProcessingException e)");

        assertThat(result.fixedContent()).isEqualTo("catch (tools.jackson.core.JacksonException e)");
    }

    @Test
    void fixLeavesContentWithoutOldPackageReferencesUnchanged() {
        JacksonMigrationRules.FixResult result = JacksonMigrationRules.fix("no jackson here");

        assertThat(result.fixedContent()).isEqualTo("no jackson here");
        assertThat(result.occurrences()).isZero();
    }
}
