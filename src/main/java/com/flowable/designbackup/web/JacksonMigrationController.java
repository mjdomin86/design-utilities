package com.flowable.designbackup.web;

import com.flowable.designbackup.migration.JacksonFixRequest;
import com.flowable.designbackup.migration.JacksonFixResponse;
import com.flowable.designbackup.migration.JacksonMigrationService;
import com.flowable.designbackup.migration.JacksonScanRequest;
import com.flowable.designbackup.migration.JacksonScanResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scans a workspace for leftover Jackson 2 references and fixes the models
 * the user selects.
 */
@RestController
@RequestMapping("/api/jackson-migration")
public class JacksonMigrationController {

    private final JacksonMigrationService migrationService;

    public JacksonMigrationController(JacksonMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/scan")
    public JacksonScanResponse scan(@Valid @RequestBody JacksonScanRequest request) {
        return migrationService.scan(request);
    }

    @PostMapping("/fix")
    public JacksonFixResponse fix(@Valid @RequestBody JacksonFixRequest request) {
        return migrationService.fix(request);
    }
}
