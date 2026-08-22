package com.rensights.controller;

import com.rensights.model.Building;
import com.rensights.repository.BuildingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Building name suggestions for the analysis request form.
 *
 * <p>Read-only and deliberately thin: the form calls this on every keystroke (debounced), so it
 * returns a short, already-ranked list of plain names. The catalogue itself is maintained in the
 * admin app, and holds nothing but names.
 */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    /** Enough to fill a suggestion dropdown without turning it into a browsing list. */
    private static final int MAX_SUGGESTIONS = 10;

    /** Below this, a query matches most of the catalogue and the suggestions are noise. */
    private static final int MIN_QUERY_LENGTH = 2;

    private final BuildingRepository buildingRepository;

    public BuildingController(BuildingRepository buildingRepository) {
        this.buildingRepository = buildingRepository;
    }

    /** Names matching what the user has typed so far. */
    @GetMapping("/search")
    public List<String> search(@RequestParam("q") String q) {
        String query = q == null ? "" : q.trim().toLowerCase();
        if (query.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        return buildingRepository.search(query, PageRequest.of(0, MAX_SUGGESTIONS))
            .stream()
            .map(Building::getName)
            .toList();
    }
}
