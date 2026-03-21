package com.garden.planner.data;

import com.garden.planner.core.model.PlantSpecies;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SeedDataLoaderTest {

    @Test
    void load_validCsv_returnsExpectedData() throws Exception {
        String csv = "Plant,Name,Width (in),Height (max)\n"
                + "Tomato,Cherry Tomato,6,48\n"
                + "Herb,Basil,3,18\n"
                + "Squash,Zucchini,12,\n"; // missing height → default 999

        SeedDataLoader loader = new SeedDataLoader();
        Map<PlantSpecies, int[]> data = loader.load(new StringReader(csv));

        assertThat(data).hasSize(3);

        PlantSpecies tomato = new PlantSpecies("Tomato", "Cherry Tomato");
        assertThat(data).containsKey(tomato);
        assertThat(data.get(tomato)).containsExactly(6, 48);

        PlantSpecies basil = new PlantSpecies("Herb", "Basil");
        assertThat(data.get(basil)).containsExactly(3, 18);

        PlantSpecies squash = new PlantSpecies("Squash", "Zucchini");
        assertThat(data.get(squash)[0]).isEqualTo(12);
        assertThat(data.get(squash)[1]).isEqualTo(999); // default height
    }

    @Test
    void load_missingWidth_skipsRow() throws Exception {
        String csv = "Plant,Name,Width (in),Height (max)\n"
                + "Tomato,Cherry Tomato,,48\n"
                + "Herb,Basil,3,18\n";

        SeedDataLoader loader = new SeedDataLoader();
        Map<PlantSpecies, int[]> data = loader.load(new StringReader(csv));

        assertThat(data).hasSize(1);
        assertThat(data).containsKey(new PlantSpecies("Herb", "Basil"));
    }

    @Test
    void load_emptyPlantOrName_skipsRow() throws Exception {
        String csv = "Plant,Name,Width (in),Height (max)\n"
                + ",Cherry Tomato,6,48\n"
                + "Herb,,3,18\n"
                + "Valid,Plant,4,24\n";

        SeedDataLoader loader = new SeedDataLoader();
        Map<PlantSpecies, int[]> data = loader.load(new StringReader(csv));

        assertThat(data).hasSize(1);
        assertThat(data).containsKey(new PlantSpecies("Valid", "Plant"));
    }

    @Test
    void load_nonNumericWidth_skipsRow() throws Exception {
        String csv = "Plant,Name,Width (in),Height (max)\n"
                + "Tomato,Cherry Tomato,N/A,48\n"
                + "Herb,Basil,3,18\n";

        SeedDataLoader loader = new SeedDataLoader();
        Map<PlantSpecies, int[]> data = loader.load(new StringReader(csv));

        assertThat(data).hasSize(1);
    }
}
