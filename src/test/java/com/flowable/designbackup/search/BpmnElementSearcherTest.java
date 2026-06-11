package com.flowable.designbackup.search;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BpmnElementSearcherTest {

    private static String bpmnXml;

    private final BpmnElementSearcher searcher = new BpmnElementSearcher();

    @BeforeAll
    static void loadFixture() throws Exception {
        try (var stream = BpmnElementSearcherTest.class.getResourceAsStream("/fixtures/creditCheck1.bpmn")) {
            bpmnXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void containsFindsOnlyElementsMentioningTheTerm() {
        List<ElementMatch> matches = searcher.findMatchingElements(
                bpmnXml, SearchCondition.CONTAINS, "Determine Risk", null);

        assertThat(matches).extracting(ElementMatch::elementId).containsExactly("AgentTask_4");
        assertThat(matches.getFirst().elementType()).isEqualTo("serviceTask");
        assertThat(matches.getFirst().flowableType()).isEqualTo("agent");
    }

    @Test
    void notContainsEvaluatesEachElementOfSelectedTypeIndividually() {
        // The GEN-902 example: service tasks that do NOT mention the term.
        List<ElementMatch> matches = searcher.findMatchingElements(
                bpmnXml, SearchCondition.NOT_CONTAINS, "GEN-902", "serviceTask");

        assertThat(matches).extracting(ElementMatch::elementId).containsExactly("AgentTask_4");
    }

    @Test
    void notContainsExcludesElementsThatMentionTheTerm() {
        List<ElementMatch> matches = searcher.findMatchingElements(
                bpmnXml, SearchCondition.NOT_CONTAINS, "Determine Risk", "serviceTask");

        assertThat(matches).isEmpty();
    }

    @Test
    void elementTypeFilterMatchesFlowableTypeAttribute() {
        List<ElementMatch> matches = searcher.findMatchingElements(
                bpmnXml, SearchCondition.NOT_CONTAINS, "GEN-902", "agent");

        assertThat(matches).extracting(ElementMatch::elementId).containsExactly("AgentTask_4");
    }

    @Test
    void equalsMatchesExactTokenInsideElement() {
        List<ElementMatch> matches = searcher.findMatchingElements(
                bpmnXml, SearchCondition.EQUALS, "FormTask_7", "userTask");

        assertThat(matches).extracting(ElementMatch::elementId).containsExactly("FormTask_7");
        // Partial tokens do not match: "FormTas" is a fragment of both
        // FormTask_7 (id) and FormTask (stencil id), never a whole token.
        assertThat(searcher.findMatchingElements(bpmnXml, SearchCondition.EQUALS, "FormTas", "userTask"))
                .isEmpty();
    }

    @Test
    void unknownElementTypeFilterYieldsNoMatches() {
        List<ElementMatch> matches = searcher.findMatchingElements(
                bpmnXml, SearchCondition.CONTAINS, "Determine Risk", "scriptTask");

        assertThat(matches).isEmpty();
    }
}
