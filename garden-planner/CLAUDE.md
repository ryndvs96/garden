# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All commands should be run from `garden-planner/` (the Maven project root):

```bash
# Run the app
mvn -f /Users/ryan/Code/garden/garden-planner/pom.xml javafx:run

# Run all tests
mvn -f /Users/ryan/Code/garden/garden-planner/pom.xml clean test

# Run a single test class
mvn -f /Users/ryan/Code/garden/garden-planner/pom.xml test -Dtest=CanonicalBedTest

# Build fat JAR
mvn -f /Users/ryan/Code/garden/garden-planner/pom.xml clean package
```

## Architecture

### Overview

A JavaFX desktop app that optimizes plant placement in raised garden beds using a hexagonal grid. Seeds/plants are loaded from CSVs, the optimizer searches for the best non-overlapping placement, and the result is saved as JSON.

### Hex Grid

`core/geometry/HexGrid.java` is central to correctness. The grid uses **odd-r offset coordinates** (odd rows shifted right by 0.5). All footprint math must use cube-coordinate disk enumeration — `computeCells()` converts center to cube coords, enumerates `max(|dx|, |dz|, |dx+dz|) ≤ r`, then converts back. Never use the old `diskOffsets()` method for footprint computation — it produces a diamond in offset space, causing visual misalignment.

Zones: `Back=(rows 0–8)`, `Middle=(rows 9–18)`, `Front=(rows 19–27)`. Strict plants must have all footprint cells within their zone; loose/understory plants clip to zone bounds.

### Placement Search

`core/search/` contains two engines both implementing `SearchEngine`:
- `LocalSearchEngine`: multi-start parallel local search
- `GreedyLnsEngine`: greedy construction + large-neighborhood search

`PlacementState` is the mutable model: tracks placed/unplaced lists, a `strictGrid[r][c]` occupancy count (for overlap detection), species counts, and score. Use `addPlant()`/`removePlant()` to mutate state.

`LocalSearchEngine.makePlacedPlant()` is a static helper used both by search and by the GUI when manually repositioning plants.

### GUI

`gui/AppController.java` is the main editor controller. Key patterns:
- **Arrow keys / Tab**: Must use `addEventFilter(KeyEvent.KEY_PRESSED, ...)` — `setOnKeyPressed` fires after `ScrollPane` consumes arrow keys.
- **Locked plants across regeneration**: Capture locked `PlacedPlant` instances before search, restore them via `state.addPlant(lp)` after `result.state()` is received.
- **Plant colors**: Assigned deterministically in `BedCanvas` via `(key.hashCode() & Integer.MAX_VALUE) % PALETTE.size()` where key = `plantType + "|" + plantName`. Never use a stateful counter.
- `selectPlant(int idx)` is the single point for updating selection UI (header label, lock checkbox, edit button, status bar, stats panel).

`gui/BedCanvas.java` renders the hex grid on a JavaFX `Canvas`. `cellSize` is mutable (default 12px, range 6–30) to support zoom. `redraw()` draws: zone fills → grid cells → bed boundary → placed plants → selection highlight (last, so it sits on top).

`data/StateSerializer.java` recomputes cell positions from `(row, col)` on load using `HexGrid.computeCells()` — saved cell arrays in JSON are ignored. This upgrades old saves to the current hex algorithm automatically.

### Data Flow

```
CSV files (data/20xx/)  →  SeedDataLoader  →  List<PlantSpecies>
                                                    ↓
                                            PlantInstance list
                                                    ↓
                                         SearchEngine.search()
                                                    ↓
                                          PlacementState (JSON)
                                                    ↓
                                         StateSerializer.save()
```

Saves live at `../saves/` relative to the Maven project root (i.e., `garden/saves/`). `StartScreenController.resolveSavesDir()` tries `saves/` first, falls back to `../saves/`.
