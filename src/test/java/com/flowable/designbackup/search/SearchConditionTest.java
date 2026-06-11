package com.flowable.designbackup.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchConditionTest {

    @Test
    void containsMatchesSubstring() {
        assertThat(SearchCondition.CONTAINS.matches("uses GEN-902 reference", "GEN-902")).isTrue();
        assertThat(SearchCondition.CONTAINS.matches("no reference here", "GEN-902")).isFalse();
    }

    @Test
    void notContainsIsInverseOfContains() {
        assertThat(SearchCondition.NOT_CONTAINS.matches("no reference here", "GEN-902")).isTrue();
        assertThat(SearchCondition.NOT_CONTAINS.matches("uses GEN-902 reference", "GEN-902")).isFalse();
    }

    @Test
    void equalsMatchesExactTokenOnly() {
        assertThat(SearchCondition.EQUALS.matches("key=\"GEN-902\"", "GEN-902")).isTrue();
        assertThat(SearchCondition.EQUALS.matches("standalone GEN-902 here", "GEN-902")).isTrue();
        assertThat(SearchCondition.EQUALS.matches("key=\"GEN-9021\"", "GEN-902")).isFalse();
        assertThat(SearchCondition.EQUALS.matches("key=\"XGEN-902\"", "GEN-902")).isFalse();
    }
}
