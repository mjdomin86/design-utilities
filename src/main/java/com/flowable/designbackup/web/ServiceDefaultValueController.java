package com.flowable.designbackup.web;

import com.flowable.designbackup.defaults.DefaultValueRequest;
import com.flowable.designbackup.defaults.DefaultValueResponse;
import com.flowable.designbackup.defaults.ServiceDefaultValueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adds default values to the number output parameters of an app's service
 * models and re-imports the app into Flowable Design.
 */
@RestController
public class ServiceDefaultValueController {

    private final ServiceDefaultValueService defaultValueService;

    public ServiceDefaultValueController(ServiceDefaultValueService defaultValueService) {
        this.defaultValueService = defaultValueService;
    }

    @PostMapping("/api/service-default-values")
    public DefaultValueResponse addDefaultValues(@Valid @RequestBody DefaultValueRequest request) {
        return defaultValueService.addDefaultValues(request);
    }
}
