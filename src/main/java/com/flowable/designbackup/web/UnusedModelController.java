package com.flowable.designbackup.web;

import com.flowable.designbackup.usage.UnusedModelService;
import com.flowable.designbackup.usage.UnusedModelsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UnusedModelController {

    private final UnusedModelService unusedModelService;

    public UnusedModelController(UnusedModelService unusedModelService) {
        this.unusedModelService = unusedModelService;
    }

    @GetMapping("/api/workspaces/{workspaceKey}/unused-models")
    public UnusedModelsResponse findUnusedModels(
            @PathVariable String workspaceKey,
            @RequestParam(required = false) List<String> modelTypes) {
        return unusedModelService.findUnusedModels(workspaceKey, modelTypes == null ? List.of() : modelTypes);
    }
}
