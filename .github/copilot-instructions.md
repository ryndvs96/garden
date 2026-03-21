# Garden Planner - AI Coding Guide

## Project Overview
A static garden planning web app (GitHub Pages ready) that lets users track seeds and visualize planting schedules across garden beds. No backend—all data is CSV-based and processed client-side.

**Key Components:**
- **Seeds View**: Filterable table of seed inventory with company links
- **Beds View**: Canvas-based timeline showing when to sow/transplant/harvest plants in specific garden locations
- **Multi-year Support**: 2025 and 2026 data with different CSV formats

## Architecture & Data Flow

### CSV-Based Data Structure
- **Seeds CSV** (`data/{year}/seeds.csv`): Plant inventory with metadata (company links, colors, heights, sow dates)
- **Beds CSV** (`data/{year}/beds.csv`): Garden layout + weekly timeline of sowing/harvesting events

**Key Fields in Beds CSV:**
- **Position**: Back/Middle/Front (raised beds) or General (other areas)
- **Placed/Sown**: Boolean status flags
- **Plant Type & Name**: Links to seeds CSV
- **Event indicators**: Date (sow), "Sp" (sprout), "T" (transplant), "H" (harvest), "--------" (active week)

### View Switching Logic
```javascript
currentYear → loadYear() → fetch seeds & beds CSV
  → parseCSV() / parseBedsCsv() 
  → populate Seeds table OR Beds canvas (based on currentView)
```

**State variables** in global scope:
- `currentYear`: '2025' or '2026'
- `currentView`: 'seeds' or 'beds'
- `currentData`: Parsed seeds array
- `bedsData`: {areas, dateColumns (week dates), format}
- `currentWeekIndex`: Selected week in timeline

## Critical Patterns & Conventions

### CSV Parsing
Custom parser handles quoted fields (seeds contain commas):
```javascript
parseCSVLine() // Respects quotes, splits on unquoted commas
parseCSV() // Returns array of objects with headers as keys
```

### Two Beds CSV Formats
2026 format differs from 2025:
- **2026**: Row 0 = week numbers, Row 1 = headers, data starts Row 2
- **2025**: Row 0 = headers, data starts Row 1
- Code branches on `format` parameter in `parseBedsCsv()`

### Event Timeline & Week Selection
- `findCurrentWeekIndex()`: Auto-selects nearest week to today
- `renderWeekSelector()`: Draws interactive timeline bars (height = event count)
- Click/drag to jump between weeks
- `currentWeekIndex` drives sow info panel + bed highlighting

### Plant Color Mapping
- Colors fetched from seeds CSV, mapped to hex codes (`colorMap`)
- Deterministic per-plant selection via string hash: `getPlantColor(plantName)`
- Cached in `plantColorCache` (cleared on year change)

### Beds Visualization (Konva.js)
- `renderBeds()`: Builds garden layout based on area definitions in `bedSizes`
- Sub-units: Some areas (Front Yard) split into grid/vertical sections
- Flow wraps plants to next row if width exceeded
- `updateBedHighlights()`: On week change, pulse sow-date plants (gold border, bright color)

### Filter & Search
- Seeds table filters by search text, category (Plant type), "Allocated?" flag
- Filters applied in `renderTable()` before rendering rows

## Developer Workflows

### Local Development
```bash
python3 -m http.server 8000
# Navigate to http://localhost:8000
```

### Adding a New Year
1. Create `data/{year}/seeds.csv` and `data/{year}/beds.csv`
2. Add config to `yearConfig` object with correct `bedsFormat` ('2025' or '2026')
3. Set correct column names for seeds data (2025 uses "Where to sow", 2026 uses "Recommended")

### CSV Data Validation
- Bed areas must match keys in `bedSizes` (Right Raised Bed, Left Raised Bed, etc.)
- Week dates in beds.csv headers must match format "M/D" (e.g., "1/31", "2/14")
- Position headers must appear as area headers (plantType filled, other cols empty)

## Key Files to Know
- **app.js** (1089 lines): All rendering, data parsing, event handlers
- **data/2026/seeds.csv**: Current year seed inventory (54 plants)
- **data/2026/beds.csv**: Garden plan with 26-week timeline
- **index.html**: Tab & filter UI structure
- **styles.css**: Styling (colors, layout, responsive)

## Important Notes for Modifications
- **No HTML generation for area layouts**: Areas defined statically in `bedSizes` object (app.js ~line 750)
- **Color-coded event types**: Sprout/transplant/harvest use different visual styles
- **Date parsing**: Uses MM/DD format from CSV header row, converted to date for "this week" logic
- **Performance**: Plant color cache prevents repeated hashing; batch draws on Konva layer reduce redraws
