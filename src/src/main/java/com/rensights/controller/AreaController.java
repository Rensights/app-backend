package com.rensights.controller;

import com.rensights.model.Area;
import com.rensights.repository.AreaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The area / district list behind the analysis request form's dropdown.
 *
 * <p>Returns every area in one call: it is a select, not a type-ahead, so the client needs the
 * full list. Names only, alphabetical.
 */
@RestController
@RequestMapping("/api/areas")
public class AreaController {

    private final AreaRepository areaRepository;

    public AreaController(AreaRepository areaRepository) {
        this.areaRepository = areaRepository;
    }

    @GetMapping
    public List<String> list() {
        return areaRepository.findAllByOrderByNameAsc().stream().map(Area::getName).toList();
    }
}
