package com.flowable.designbackup.web;

import com.flowable.designbackup.operations.OperationSearchResponse;
import com.flowable.designbackup.operations.ServiceOperationSearchService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class OperationController {

    private final ServiceOperationSearchService operationSearchService;

    public OperationController(ServiceOperationSearchService operationSearchService) {
        this.operationSearchService = operationSearchService;
    }

    @GetMapping("/api/workspaces/{workspaceKey}/apps/{appKey}/service-operations")
    public OperationSearchResponse findOperations(
            @PathVariable String workspaceKey,
            @PathVariable String appKey,
            @RequestParam @NotBlank(message = "method is required") String method,
            @RequestParam @NotBlank(message = "path is required") String path) {
        return operationSearchService.findOperations(workspaceKey, appKey, method, path);
    }
}
