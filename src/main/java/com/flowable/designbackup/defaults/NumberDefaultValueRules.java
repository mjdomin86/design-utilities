package com.flowable.designbackup.defaults;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Makes every number output parameter of a service model fall back to a
 * default value when the response value is missing or null, the same way
 * Flowable Design does when "Set to default" is picked for both cases (see
 * examples/service-testService.service).
 *
 * <p>A parameter that already falls back to a non-blank default keeps its
 * own value; only the cases that do not are changed.
 */
public final class NumberDefaultValueRules {

    static final String SET_TO_DEFAULT = "setToDefault";

    /**
     * Integer, Long, Big integer, Big decimal and Double, compared after
     * {@link #normalizeType} so "bigDecimal", "big-decimal" or "BigDecimal"
     * all match.
     */
    private static final Set<String> NUMBER_TYPES = Set.of("integer", "long", "biginteger", "bigdecimal", "double");

    private static final List<FallbackFields> FALLBACKS = List.of(
            new FallbackFields("missingValueConfig", "missingValueDefault"),
            new FallbackFields("nullValueConfig", "nullValueDefault"));

    private NumberDefaultValueRules() {
    }

    /**
     * @return a copy of {@code serviceModel} with the defaults applied, plus
     *         one change per parameter that was updated; {@code serviceModel}
     *         itself is left untouched.
     */
    public static Result apply(JsonNode serviceModel, String defaultValue) {
        JsonNode updated = serviceModel.deepCopy();
        List<ParameterDefaultChange> changes = new ArrayList<>();
        for (JsonNode operation : updated.path("operations")) {
            for (JsonNode parameter : operation.path("outputParameters")) {
                if (parameter instanceof ObjectNode objectParameter
                        && isNumberType(text(parameter, "type"))
                        && applyDefaults(objectParameter, defaultValue)) {
                    changes.add(new ParameterDefaultChange(text(updated, "key"), text(updated, "name"),
                            text(operation, "key"), text(operation, "name"),
                            text(parameter, "name"), text(parameter, "type")));
                }
            }
        }
        return new Result(updated, changes);
    }

    static boolean isNumberType(String type) {
        return type != null && NUMBER_TYPES.contains(normalizeType(type));
    }

    private static String normalizeType(String type) {
        return type.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean applyDefaults(ObjectNode parameter, String defaultValue) {
        boolean changed = false;
        for (FallbackFields fields : FALLBACKS) {
            boolean alreadyDefaulted = SET_TO_DEFAULT.equals(text(parameter, fields.config()))
                    && hasText(text(parameter, fields.defaultValue()));
            if (!alreadyDefaulted) {
                parameter.put(fields.config(), SET_TO_DEFAULT);
                parameter.put(fields.defaultValue(), defaultValue);
                changed = true;
            }
        }
        return changed;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record FallbackFields(String config, String defaultValue) {
    }

    public record Result(JsonNode updatedModel, List<ParameterDefaultChange> changes) {

        public Result {
            changes = List.copyOf(changes);
        }
    }
}
