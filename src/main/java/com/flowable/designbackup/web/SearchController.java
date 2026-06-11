package com.flowable.designbackup.web;

import com.flowable.designbackup.search.ModelSearchService;
import com.flowable.designbackup.search.SearchRequest;
import com.flowable.designbackup.search.SearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final ModelSearchService modelSearchService;

    public SearchController(ModelSearchService modelSearchService) {
        this.modelSearchService = modelSearchService;
    }

    @PostMapping
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return modelSearchService.search(request);
    }
}
