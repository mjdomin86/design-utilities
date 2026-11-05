package com.flowable.designbackup.defaults;

import java.util.List;

/**
 * Outcome of adding default values to an app's service models. {@code
 * imported} is false when nothing needed changing, in which case the app was
 * not re-imported.
 */
public record DefaultValueResponse(
        String workspaceKey,
        String appKey,
        String defaultValue,
        int serviceModelsScanned,
        boolean imported,
        List<ParameterDefaultChange> changes,
        List<ServiceModelFailure> failures) {

    public DefaultValueResponse {
        changes = changes == null ? List.of() : List.copyOf(changes);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
