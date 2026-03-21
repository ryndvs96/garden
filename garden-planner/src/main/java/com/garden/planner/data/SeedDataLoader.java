package com.garden.planner.data;

import com.garden.planner.core.model.PlantSpecies;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads plant dimension data from a Seeds CSV file.
 * Returns Map<PlantSpecies, int[]> where int[] is {widthIn, heightIn}.
 */
public class SeedDataLoader {
    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);
    private static final int DEFAULT_WIDTH = 12;
    private static final int DEFAULT_HEIGHT = 999;

    public Map<PlantSpecies, int[]> load(String csvPath) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(csvPath), StandardCharsets.UTF_8)) {
            return load(reader);
        }
    }

    public Map<PlantSpecies, int[]> load(Reader reader) throws IOException {
        Map<PlantSpecies, int[]> data = new LinkedHashMap<>();
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setIgnoreSurroundingSpaces(true)
                .build()
                .parse(reader);

        for (CSVRecord record : records) {
            String plant = get(record, "Plant").trim();
            String name = get(record, "Name").trim();
            String wStr = get(record, "Width (in)").trim();
            String hStr = get(record, "Height (max)").trim();

            if (plant.isEmpty() || name.isEmpty() || wStr.isEmpty()) continue;

            int width;
            try {
                width = Integer.parseInt(wStr);
            } catch (NumberFormatException e) {
                continue;
            }

            int height;
            try {
                height = Integer.parseInt(hStr);
            } catch (NumberFormatException e) {
                height = DEFAULT_HEIGHT;
            }

            data.put(new PlantSpecies(plant, name), new int[]{width, height});
        }

        log.info("Loaded {} plants from seed data", data.size());
        return data;
    }

    private String get(CSVRecord record, String header) {
        try {
            return record.get(header);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
