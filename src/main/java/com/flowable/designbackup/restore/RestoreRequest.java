package com.flowable.designbackup.restore;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to restore app export zips from a local directory into a
 * workspace. {@code workspaceKey} is optional: empty means the
 * {@code default} workspace, unless {@code createWorkspace} is set, in
 * which case {@code workspaceKey} is the key of the new workspace to create.
 */
public record RestoreRequest(
        @NotBlank(message = "sourceDirectory is required") String sourceDirectory,
        String workspaceKey,
        boolean createWorkspace,
        String workspaceName) {
}
