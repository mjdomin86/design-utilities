package com.flowable.designbackup.web;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.ModelSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lookup endpoints backing the UI dropdowns.
 */
@RestController
public class WorkspaceController {

    private final DesignApiClient designApiClient;

    public WorkspaceController(DesignApiClient designApiClient) {
        this.designApiClient = designApiClient;
    }

    @GetMapping("/api/workspaces")
    public List<ModelSummary> listWorkspaces() {
        return designApiClient.listWorkspaces();
    }

    @GetMapping("/api/workspaces/{workspaceKey}/apps")
    public List<ModelSummary> listApps(@PathVariable String workspaceKey) {
        return designApiClient.listApps(workspaceKey);
    }
}
