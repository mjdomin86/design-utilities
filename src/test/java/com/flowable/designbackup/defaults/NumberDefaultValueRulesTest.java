package com.flowable.designbackup.defaults;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NumberDefaultValueRulesTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void addsDefaultsToNumberParameterWithoutThemAndKeepsExistingOnes() throws Exception {
        // Arrange
        JsonNode model;
        try (InputStream in = getClass().getResourceAsStream("/examples/service-testService.service")) {
            model = mapper.readTree(in);
        }

        // Act
        NumberDefaultValueRules.Result result = NumberDefaultValueRules.apply(model, "0");

        // Assert
        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.serviceModelKey()).isEqualTo("testService");
            assertThat(change.operationKey()).isEqualTo("withoutDefualt");
            assertThat(change.parameterName()).isEqualTo("number2");
        });
        JsonNode updated = result.updatedModel().path("operations").get(1).path("outputParameters").get(0);
        assertThat(updated.get("missingValueConfig").asString()).isEqualTo("setToDefault");
        assertThat(updated.get("nullValueConfig").asString()).isEqualTo("setToDefault");
        assertThat(updated.get("missingValueDefault").asString()).isEqualTo("0");
        assertThat(updated.get("nullValueDefault").asString()).isEqualTo("0");
        assertThat(result.updatedModel().path("operations").get(0))
                .isEqualTo(model.path("operations").get(0));
    }

    @Test
    void leavesOriginalModelUntouched() throws Exception {
        // Arrange
        JsonNode model = mapper.readTree(parameterJson("long", "ignore", null));
        JsonNode original = model.deepCopy();

        // Act
        NumberDefaultValueRules.apply(model, "5");

        // Assert
        assertThat(model).isEqualTo(original);
    }

    @Test
    void ignoresNonNumberParameters() throws Exception {
        // Arrange
        JsonNode model = mapper.readTree(parameterJson("string", "ignore", null));

        // Act
        NumberDefaultValueRules.Result result = NumberDefaultValueRules.apply(model, "0");

        // Assert
        assertThat(result.changes()).isEmpty();
        assertThat(result.updatedModel()).isEqualTo(model);
    }

    @Test
    void fillsBlankDefaultEvenWhenConfigIsAlreadySetToDefault() throws Exception {
        // Arrange
        JsonNode model = mapper.readTree(parameterJson("double", "setToDefault", ""));

        // Act
        NumberDefaultValueRules.Result result = NumberDefaultValueRules.apply(model, "7");

        // Assert
        JsonNode parameter = result.updatedModel().path("operations").get(0).path("outputParameters").get(0);
        assertThat(result.changes()).hasSize(1);
        assertThat(parameter.get("missingValueDefault").asString()).isEqualTo("7");
    }

    @ParameterizedTest
    @ValueSource(strings = {"integer", "long", "bigInteger", "bigDecimal", "double", "Big decimal", "big-integer"})
    void recognisesAllNumberTypes(String type) {
        assertThat(NumberDefaultValueRules.isNumberType(type)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"string", "boolean", "json", "date"})
    void rejectsNonNumberTypes(String type) {
        assertThat(NumberDefaultValueRules.isNumberType(type)).isFalse();
    }

    private static String parameterJson(String type, String config, String defaultValue) {
        String defaults = defaultValue == null ? ""
                : ",\"missingValueDefault\":\"" + defaultValue + "\",\"nullValueDefault\":\"" + defaultValue + "\"";
        return """
                {"key":"svc","name":"Svc","operations":[{"key":"op","name":"Op","outputParameters":[
                  {"name":"p","type":"%s","missingValueConfig":"%s","nullValueConfig":"%s"%s}]}]}
                """.formatted(type, config, config, defaults);
    }
}
