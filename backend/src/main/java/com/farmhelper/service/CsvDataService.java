package com.farmhelper.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CsvDataService {

    @Value("${farm.csv-dir}")
    private String csvDir;

    private Path csvPath;

    private List<Map<String, String>> farmLevels = List.of();
    private List<Map<String, String>> stall = List.of();
    private List<Map<String, String>> land = List.of();
    private List<Map<String, String>> crops = List.of();
    private List<Map<String, String>> cultivation = List.of();
    private List<Map<String, String>> mutationRates = List.of();
    private List<Map<String, String>> rewards = List.of();

    @PostConstruct
    public void load() throws IOException {
        csvPath = Paths.get(csvDir).toAbsolutePath().normalize();
        farmLevels = loadFile("farm_levels.csv");
        stall = loadFile("stall.csv");
        land = loadFile("land.csv");
        crops = loadFile("crops.csv");
        cultivation = loadFile("cultivation.csv");
        mutationRates = loadFile("mutation_rates.csv");
        rewards = loadFile("rewards.csv");
    }

    private List<Map<String, String>> loadFile(String filename) throws IOException {
        Path file = csvPath.resolve(filename);
        if (!Files.exists(file)) {
            throw new IOException("找不到 CSV 文件: " + file);
        }

        // CSV 以 UTF-8 BOM 导出，需去掉 BOM 避免表头错位
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        try (Reader reader = new java.io.StringReader(content);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : parser.getHeaderNames()) {
                    row.put(header, record.get(header));
                }
                rows.add(row);
            }
            return List.copyOf(rows);
        }
    }

    public List<Map<String, String>> getFarmLevels() {
        return farmLevels;
    }

    public List<Map<String, String>> getStall() {
        return stall;
    }

    public List<Map<String, String>> getLand() {
        return land;
    }

    public List<Map<String, String>> getCrops() {
        return crops;
    }

    public Optional<Map<String, String>> getCropByName(String name) {
        return crops.stream()
                .filter(row -> name.equals(row.get("name")))
                .findFirst();
    }

    public List<Map<String, String>> getCultivation() {
        return cultivation;
    }

    public Optional<Map<String, String>> getCultivationByCrop(String cropName) {
        return cultivation.stream()
                .filter(row -> cropName.equals(row.get("crop")))
                .findFirst();
    }

    public List<Map<String, String>> getMutationRates() {
        return mutationRates;
    }

    public List<Map<String, String>> getRewards() {
        return rewards;
    }
}
