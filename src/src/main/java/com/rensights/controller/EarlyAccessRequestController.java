package com.rensights.controller;

import com.rensights.dto.EarlyAccessRequestCreate;
import com.rensights.dto.EarlyAccessRequestDTO;
import com.rensights.service.EarlyAccessRequestService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/early-access")
@RequiredArgsConstructor
public class EarlyAccessRequestController {

    private static final Logger logger = LoggerFactory.getLogger(EarlyAccessRequestController.class);
    private final EarlyAccessRequestService earlyAccessRequestService;

    @PostMapping
    public ResponseEntity<EarlyAccessRequestDTO> create(@RequestBody EarlyAccessRequestCreate request) {
        EarlyAccessRequestDTO created = earlyAccessRequestService.create(request);
        logger.info("Early access request submitted for email: {}", request.getEmail());
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<Page<EarlyAccessRequestDTO>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(earlyAccessRequestService.list(pageable));
    }
}
