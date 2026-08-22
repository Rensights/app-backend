package com.rensights.controller;

import com.rensights.model.Building;
import com.rensights.repository.BuildingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Building suggestions for the analysis request form.
 *
 * <p>Read-only and deliberately thin: the form calls this on every keystroke (debounced), so it
 * returns a short, already-ranked list and nothing else. The catalogue itself is maintained in
 * the admin app.
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

    /**
     * Suggestions for what the user has typed so far.
     *
     * @param q    the partial building name
     * @param area optional district filter, when the form already has one selected
     */
    @GetMapping("/search")
    public List<Map<String, String>> search(@RequestParam("q") String q,
                                            @RequestParam(value = "area", required = false) String area) {
        String query = q == null ? "" : q.trim().toLowerCase();
        if (query.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        PageRequest limit = PageRequest.of(0, MAX_SUGGESTIONS);
        List<Building> matches = area != null && !area.isBlank()
            ? buildingRepository.searchInArea(query, area.trim().toLowerCase(), limit)
            : buildingRepository.search(query, limit);

        // If nothing matches inside the selected area, fall back to the whole catalogue rather
        // than showing an empty list - the area on the building row may simply be missing.
        if (matches.isEmpty() && area != null && !area.isBlank()) {
            matches = buildingRepository.search(query, limit);
        }

        return matches.stream().map(this::toSuggestion).toList();
    }

    private Map<String, String> toSuggestion(Building building) {
        // LinkedHashMap-free: Map.of drops nulls, so blanks are normalised to empty strings.
        return Map.of(
            "name", building.getName() == null ? "" : building.getName(),
            "area", building.getArea() == null ? "" : building.getArea(),
            "city", building.getCity() == null ? "" : building.getCity(),
            "developer", building.getDeveloper() == null ? "" : building.getDeveloper()
        );
    }
}
