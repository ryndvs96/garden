# Garden 2026

A static webpage to view and filter my 2026 garden planting schedule.

## Features

- Filter by sow date, plant type, or search by name
- Toggle between all plants or allocated only
- Works entirely in the browser (no server required)

## Data

Edit `garden2026.csv` to update your planting data. Columns include:
- Plant type and variety
- Seed company
- Sow dates and germination info
- Height and growing recommendations

## Local Preview

```
python3 -m http.server 8000
```

Then open http://localhost:8000
