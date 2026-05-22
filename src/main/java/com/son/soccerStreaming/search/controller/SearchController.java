package com.son.soccerStreaming.search.controller;

import com.son.soccerStreaming.search.dto.SearchResponseDto;
import com.son.soccerStreaming.search.dto.SearchType;
import com.son.soccerStreaming.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search", description = "Team, player, and fixture search API")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Global search", description = "Searches teams, players, and fixtures for the given keyword.")
    @GetMapping
    public ResponseEntity<SearchResponseDto> search(
            @Parameter(description = "Search keyword", example = "tottenham chelsea")
            @RequestParam(name = "q", defaultValue = "") String keyword,
            @Parameter(description = "Search scope", example = "all")
            @RequestParam(name = "type", defaultValue = "all") String type
    ) {
        return ResponseEntity.ok(searchService.search(keyword, SearchType.from(type)));
    }
}
