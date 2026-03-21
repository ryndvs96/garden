package com.garden.planner.data;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads bed plant assignments from a Beds CSV file.
 * Returns Map<String, List<BedPlantSpec>> where key is bed name.
 * Only loads "Left Raised Bed" and "Right Raised Bed".
 */
public class BedAssignmentLoader {
    private static final Logger log = LoggerFactory.getLogger(BedAssignmentLoader.class);
    private static final Set<String> TARGET_BEDS = Set.of("Left Raised Bed", "Right Raised Bed");

    public record BedPlantSpec(String zone, String plantType, String plantName, int count) {}

    public Map<String, List<BedPlantSpec>> load(String csvPath) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(csvPath), StandardCharsets.UTF_8)) {
            return load(reader);
        }
    }

    public Map<String, List<BedPlantSpec>> load(Reader reader) throws IOException {
        Map<String, List<BedPlantSpec>> beds = new LinkedHashMap<>();
        String currentBed = null;

        Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                .setIgnoreSurroundingSpaces(true)
                .build()
                .parse(reader);

        for (CSVRecord record : records) {
            String loc = safeGet(record, 0).trim();
            String ptype = safeGet(record, 1).trim();
            String pname = safeGet(record, 2).trim();
            String countStr = safeGet(record, 3).trim();

            if (loc.equals("Location?")) continue;

            // Detect bed header row: col[0] empty, col[1] has bed name, col[3] empty
            if (loc.isEmpty() && !ptype.isEmpty() && countStr.isEmpty()) {
                if (TARGET_BEDS.contains(ptype)) {
                    currentBed = ptype;
                    beds.putIfAbsent(currentBed, new ArrayList<>());
                } else {
                    currentBed = null;
                }
                continue;
            }

            if (currentBed != null && !loc.isEmpty() && !ptype.isEmpty() && !pname.isEmpty()) {
                int count = 0;
                try {
                    count = countStr.isEmpty() ? 0 : Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    count = 0;
                }
                if (count > 0) {
                    beds.get(currentBed).add(new BedPlantSpec(loc, ptype, pname, count));
                }
            }
        }

        log.info("Loaded {} beds from bed assignments", beds.size());
        return beds;
    }

    private String safeGet(CSVRecord record, int index) {
        try {
            return record.size() > index ? record.get(index) : "";
        } catch (Exception e) {
            return "";
        }
    }
}
