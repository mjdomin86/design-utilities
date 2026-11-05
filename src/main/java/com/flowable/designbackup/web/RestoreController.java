package com.flowable.designbackup.web;

import com.flowable.designbackup.restore.RestoreRequest;
import com.flowable.designbackup.restore.RestoreResult;
import com.flowable.designbackup.restore.RestoreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual restore trigger. Runs synchronously and returns the full result,
 * including per-app failures.
 */
@RestController
@RequestMapping("/api/restores")
public class RestoreController {

    private final RestoreService restoreService;

    public RestoreController(RestoreService restoreService) {
        this.restoreService = restoreService;
    }

    @PostMapping
    public RestoreResult startRestore(@Valid @RequestBody RestoreRequest request) {
        return restoreService.performRestore(request);
    }
}
