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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CsvDataService {

    @Value("${farm.csv-dir}")
    private String csvDir;

    private Path csvPath;

    private List<Map<String, Object>> farmLevels = List.of();
    private List<Map<String, Object>> stall = List.of();
    private List<Map<String, Object>> land = List.of();
    private List<Map<String, Object>> crops = List.of();
    private List<Map<String, Object>> cultivation = List.of();
    private List<Map<String, Object>> mutationRates = List.of();
    private List<Map<String, Object>> rewards = List.of();

    // ── 数值字段集合 ──

    private static final Set<String> NUMERIC_CROPS = Set.of(
            "unlock_level", "seed_price", "yield_qty", "total_sell_price",
            "exp_gain", "harvest_time", "mutation_limit");

    private static final Set<String> NUMERIC_CULTIVATION = Set.of(
            "total", "lv2", "lv3", "lv4", "lv5", "lv6", "lv7", "lv8", "lv9", "lv10");

    // upgrade_cost、required_exp 含 "1.3万" 等中文单位，保持字符串
    private static final Set<String> NUMERIC_FARM_LEVELS = Set.of(
            "level");

    // upgrade_cost、gain_exp 含 "1万" 等中文单位，保持字符串
    private static final Set<String> NUMERIC_STALL = Set.of(
            "level", "required_farm_level");

    // reclaim_cost、gain_exp 含 "1.6万" 等中文单位，保持字符串
    private static final Set<String> NUMERIC_LAND = Set.of(
            "required_level");

    private static final Set<String> NUMERIC_MUTATION = Set.of(
            "multiplier", "unlock_level");

    private static final Set<String> NUMERIC_REWARDS = Set.of();

    // ── 中文数字模式 ──
    private static final Pattern CN_NUM_PATTERN = Pattern.compile(
            "^([\\d.]+)\\s*(万|亿|w|W)?$");

    @PostConstruct
    public void load() throws IOException {
        csvPath = Paths.get(csvDir).toAbsolutePath().normalize();
        farmLevels = loadFile("farm_levels.csv", NUMERIC_FARM_LEVELS);
        stall = loadFile("stall.csv", NUMERIC_STALL);
        land = loadFile("land.csv", NUMERIC_LAND);
        crops = loadFile("crops.csv", NUMERIC_CROPS);
        cultivation = loadFile("cultivation.csv", NUMERIC_CULTIVATION);
        mutationRates = loadFile("mutation_rates.csv", NUMERIC_MUTATION);
        rewards = loadFile("rewards.csv", NUMERIC_REWARDS);
    }

    private List<Map<String, Object>> loadFile(String filename, Set<String> numericFields)
            throws IOException {
        Path file = csvPath.resolve(filename);
        if (!Files.exists(file)) {
            throw new IOException("找不到 CSV 文件: " + file);
        }

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

            List<Map<String, Object>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String header : parser.getHeaderNames()) {
                    String raw = record.get(header);
                    Object value;

                    if ("harvest_time".equals(header)) {
                        // 先转为秒数整数，再作为 Long 存入
                        value = parseLongOrNull(convertHarvestTimeToSeconds(raw));
                    } else if (numericFields.contains(header)) {
                        value = parseNumber(raw);
                    } else {
                        value = raw; // 文本字段保持字符串
                    }

                    row.put(header, value);
                }
                rows.add(row);
            }
            return List.copyOf(rows);
        }
    }

    // ── 数值解析 ──

    /** 解析可能含中文单位的数字，如 "1.3万"→13000、"1.03亿"→103000000、"5%"→"5%"(保留文本)。返回 Long 或原字符串。 */
    private Object parseNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // 百分比等非纯数字文本，保留字符串
        if (raw.contains("%") || raw.contains("：") || raw.contains(":")) {
            return raw;
        }

        // 先尝试直接解析为整数
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            // 继续
        }

        // 匹配 "1.3万"、"5.0万"、"1.03亿" 等
        Matcher m = CN_NUM_PATTERN.matcher(raw);
        if (m.matches()) {
            double base = Double.parseDouble(m.group(1));
            String unit = m.group(2);
            if (unit != null) {
                if (unit.contains("亿")) {
                    base *= 100_000_000;
                } else {
                    base *= 10_000; // 万
                }
            }
            return (long) base;
        }

        // 无法解析，保留原字符串
        return raw;
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── harvest_time 转换 ──

    private String convertHarvestTimeToSeconds(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String v = raw.trim();

        Matcher secMatcher = Pattern.compile("^(\\d+)s$").matcher(v);
        if (secMatcher.matches()) return secMatcher.group(1);

        Matcher minMatcher = Pattern.compile("^(\\d+)min$").matcher(v);
        if (minMatcher.matches()) return String.valueOf(Long.parseLong(minMatcher.group(1)) * 60);

        Matcher hourMatcher = Pattern.compile("^(\\d+)h$").matcher(v);
        if (hourMatcher.matches()) return String.valueOf(Long.parseLong(hourMatcher.group(1)) * 3600);

        return v;
    }

    // ── Getter ──

    public List<Map<String, Object>> getFarmLevels() { return farmLevels; }
    public List<Map<String, Object>> getStall() { return stall; }
    public List<Map<String, Object>> getLand() { return land; }
    public List<Map<String, Object>> getCrops() { return crops; }
    public List<Map<String, Object>> getCultivation() { return cultivation; }
    public List<Map<String, Object>> getMutationRates() { return mutationRates; }
    public List<Map<String, Object>> getRewards() { return rewards; }

    public Optional<Map<String, Object>> getCropByName(String name) {
        return crops.stream()
                .filter(row -> name.equals(row.get("name")))
                .findFirst();
    }

    public Optional<Map<String, Object>> getCultivationByCrop(String cropName) {
        return cultivation.stream()
                .filter(row -> cropName.equals(row.get("crop")))
                .findFirst();
    }
}
