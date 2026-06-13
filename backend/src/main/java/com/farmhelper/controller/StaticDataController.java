package com.farmhelper.controller;

import com.farmhelper.service.CsvDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/static")
public class StaticDataController {

    private final CsvDataService csvDataService;

    public StaticDataController(CsvDataService csvDataService) {
        this.csvDataService = csvDataService;
    }

    @GetMapping("/farm-levels")
    public List<Map<String, String>> farmLevels() {
        return csvDataService.getFarmLevels();
    }

    @GetMapping("/stall")
    public List<Map<String, String>> stall() {
        return csvDataService.getStall();
    }

    @GetMapping("/land")
    public List<Map<String, String>> land() {
        return csvDataService.getLand();
    }

    @GetMapping("/crops")
    public List<Map<String, String>> crops() {
        return csvDataService.getCrops();
    }

    @GetMapping("/crops/{name}")
    public ResponseEntity<Map<String, String>> cropDetail(@PathVariable String name) {
        return csvDataService.getCropByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cultivation")
    public List<Map<String, String>> cultivation(@RequestParam(required = false) String crop) {
        if (crop != null && !crop.isBlank()) {
            return csvDataService.getCultivationByCrop(crop)
                    .map(List::of)
                    .orElse(List.of());
        }
        return csvDataService.getCultivation();
    }

    @GetMapping("/mutation-rates")
    public List<Map<String, String>> mutationRates() {
        return csvDataService.getMutationRates();
    }

    @GetMapping("/rewards")
    public List<Map<String, String>> rewards() {
        return csvDataService.getRewards();
    }
}
