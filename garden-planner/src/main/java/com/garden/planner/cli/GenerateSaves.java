package com.garden.planner.cli;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;
import com.garden.planner.core.search.*;
import com.garden.planner.data.BedAssignmentLoader;
import com.garden.planner.data.BedAssignmentLoader.BedPlantSpec;
import com.garden.planner.data.SeedDataLoader;
import com.garden.planner.data.StateSerializer;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CLI runner: loads CSVs, runs LocalSearch on each bed, saves JSON to saves/.
 * Run via: mvn exec:java -Dexec.mainClass=com.garden.planner.cli.GenerateSaves
 */
public class GenerateSaves {

    private static final String SEEDS_CSV = "/Users/ryan/Code/garden/data/2026/Gardening 2026 - Seeds to Use.csv";
    private static final String BEDS_CSV  = "/Users/ryan/Code/garden/data/2026/Gardening 2026 - Plants per Bed.csv";

    // A-Z a-z 0-9 display codes
    private static final List<String> CODES;
    static {
        List<String> c = new ArrayList<>();
        for (char ch = 'A'; ch <= 'Z'; ch++) c.add(String.valueOf(ch));
        for (char ch = 'a'; ch <= 'z'; ch++) c.add(String.valueOf(ch));
        for (int i = 0; i <= 9; i++) c.add(String.valueOf(i));
        CODES = c;
    }

    public static void main(String[] args) throws Exception {
        SeedDataLoader seedLoader = new SeedDataLoader();
        BedAssignmentLoader bedLoader = new BedAssignmentLoader();
        StateSerializer serializer = new StateSerializer();

        Map<PlantSpecies, int[]> seedData = seedLoader.load(SEEDS_CSV);
        System.out.printf("Loaded %d plant species from seeds CSV%n", seedData.size());

        Map<String, List<BedPlantSpec>> beds = bedLoader.load(BEDS_CSV);
        System.out.printf("Loaded %d beds%n", beds.size());

        File savesDir = new File("saves");
        savesDir.mkdirs();

        SearchConfig config = new SearchConfig(
            30,              // nStarts
            400,             // nIters
            PenaltyMode.CELL,
            60_000L,         // 60s timeout
            42L,             // baseSeed
            150,             // nPositions
            HexGrid.GRID_ROWS,
            HexGrid.GRID_COLS
        );

        for (Map.Entry<String, List<BedPlantSpec>> entry : beds.entrySet()) {
            String bedName = entry.getKey();
            List<BedPlantSpec> specs = entry.getValue();

            List<PlantInstance> plants = buildInstances(specs, seedData);
            System.out.printf("%n[%s] %d plant instances across %d species%n",
                bedName, plants.size(), countUnique(plants));
            System.out.printf("  Running %d starts × %d iters…%n", config.nStarts(), config.nIters());

            SearchMetrics metrics = new SearchMetrics();
            AtomicBoolean cancelled = new AtomicBoolean(false);
            LocalSearchEngine engine = new LocalSearchEngine();

            long t0 = System.currentTimeMillis();
            SearchResult result = engine.search(plants, config, metrics, cancelled);
            long elapsed = System.currentTimeMillis() - t0;

            PlacementState state = result.state();
            System.out.printf("  Done in %.1fs — placed=%d unplaced=%d score=%.1f overlaps=%d%n",
                elapsed / 1000.0,
                state.getNPlaced(),
                state.getUnplaced().size(),
                state.getScore(),
                state.countStrictOverlaps());

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"));
            String filename = bedName.toLowerCase().replace(' ', '-')
                    + "_" + ts + "_s" + (int) state.getScore() + ".json";
            File outFile = new File(savesDir, filename);
            serializer.save(state, bedName, outFile);
            System.out.printf("  Saved → saves/%s%n", filename);
        }
    }

    static List<PlantInstance> buildInstances(
            List<BedPlantSpec> specs, Map<PlantSpecies, int[]> seedData) {
        List<PlantInstance> instances = new ArrayList<>();
        Map<PlantSpecies, String> groupCodes = new LinkedHashMap<>();
        int codeIdx = 0;

        for (BedPlantSpec spec : specs) {
            PlantSpecies sp = new PlantSpecies(spec.plantType(), spec.plantName());
            int[] dims = lookupDims(sp, seedData);
            int widthIn = dims[0], heightIn = dims[1];
            boolean isStrict = heightIn > HexGrid.LOOSE_HEIGHT_THRESHOLD;

            if (!groupCodes.containsKey(sp)) {
                groupCodes.put(sp, codeIdx < CODES.size() ? CODES.get(codeIdx) : "?");
                codeIdx++;
            }
            String code = groupCodes.get(sp);

            for (int i = 1; i <= spec.count(); i++) {
                instances.add(new PlantInstance(
                    spec.zone(), spec.plantType(), spec.plantName(),
                    widthIn, heightIn, isStrict, i, code));
            }
        }

        // Diversity-first sort: (instanceIdx, strict-first, larger-first)
        instances.sort(Comparator
            .comparingInt(PlantInstance::instanceIdx)
            .thenComparing(p -> p.isStrict() ? 0 : 1)
            .thenComparingInt(p -> -p.widthIn()));
        return instances;
    }

    private static int[] lookupDims(PlantSpecies sp, Map<PlantSpecies, int[]> seedData) {
        if (seedData.containsKey(sp)) return seedData.get(sp);
        // Fuzzy fallback: find closest key
        for (Map.Entry<PlantSpecies, int[]> e : seedData.entrySet()) {
            if (e.getKey().name().equalsIgnoreCase(sp.name())) return e.getValue();
        }
        System.out.printf("  [WARN] No data for '%s %s' — using W=12in H=999in%n",
            sp.type(), sp.name());
        return new int[]{12, 999};
    }

    private static int countUnique(List<PlantInstance> plants) {
        Set<String> seen = new HashSet<>();
        for (PlantInstance p : plants) seen.add(p.plantType() + "|" + p.plantName());
        return seen.size();
    }
}
