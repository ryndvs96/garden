package com.garden.planner.core;

import com.garden.planner.core.geometry.HexGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class HexGridTest {

    @Test
    void diskOffsets_width1_singleCell() {
        List<int[]> offsets = HexGrid.diskOffsets(1);
        assertThat(offsets).hasSize(1);
        assertThat(offsets.get(0)).containsExactly(0, 0);
    }

    @Test
    void diskOffsets_width2_fourCells() {
        // width=2: r=1
        // dr=-1: rowWidth=2-1=1, dcStart=0 → (0,-1)
        // dr=0:  rowWidth=2,   dcStart=-1 → (-1,0),(0,0)
        // dr=1:  rowWidth=1,   dcStart=0  → (0,1)
        List<int[]> offsets = HexGrid.diskOffsets(2);
        assertThat(offsets).hasSize(4);
        // Verify the expected cells exist
        assertThat(offsets).anySatisfy(o -> assertThat(o).containsExactly(0, -1));
        assertThat(offsets).anySatisfy(o -> assertThat(o).containsExactly(-1, 0));
        assertThat(offsets).anySatisfy(o -> assertThat(o).containsExactly(0, 0));
        assertThat(offsets).anySatisfy(o -> assertThat(o).containsExactly(0, 1));
    }

    @Test
    void diskOffsets_width3_sevenCells() {
        // width=3: r=1
        // dr=-1: rowWidth=2, dcStart=-1 → (-1,-1),(0,-1)
        // dr=0:  rowWidth=3, dcStart=-1 → (-1,0),(0,0),(1,0)
        // dr=1:  rowWidth=2, dcStart=-1 → (-1,1),(0,1)
        List<int[]> offsets = HexGrid.diskOffsets(3);
        assertThat(offsets).hasSize(7);
    }

    @Test
    void hexDistance_sameCell_zero() {
        assertThat(HexGrid.hexDistance(5, 5, 5, 5)).isEqualTo(0);
    }

    @Test
    void hexDistance_adjacentSameRow_one() {
        // Adjacent in same row: col differs by 1
        assertThat(HexGrid.hexDistance(0, 0, 1, 0)).isEqualTo(1);
    }

    @Test
    void hexDistance_knownValues() {
        // (0,0) to (2,0): distance 2
        assertThat(HexGrid.hexDistance(0, 0, 2, 0)).isEqualTo(2);
    }

    @Test
    void gridConstants() {
        assertThat(HexGrid.GRID_ROWS).isEqualTo(28);
        assertThat(HexGrid.GRID_COLS).isEqualTo(84);
    }

    @Test
    void zones_exist() {
        assertThat(HexGrid.ZONES).containsKeys("Back", "Middle", "Front");
        assertThat(HexGrid.ZONES.get("Back").preferredLo()).isEqualTo(0);
        assertThat(HexGrid.ZONES.get("Back").preferredHi()).isEqualTo(8);
        assertThat(HexGrid.ZONES.get("Front").preferredLo()).isEqualTo(19);
    }
}
