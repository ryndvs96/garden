# Garden Planner

A static webpage to plan and visualize garden planting schedules. Hosted on GitHub Pages.

## Features

### Seeds View
- View all seeds organized by category and type
- Filter by search, category, or allocated status
- Color-coded indicators for plant colors
- Direct links to seed company product pages
- Support for multiple years (2025, 2026)

### Beds View (2026)
- Interactive timeline showing sow dates throughout the season
- Visual representation of garden bed layouts:
  - Right & Left Raised Beds (with Back/Middle/Front sections)
  - Front Yard
  - Fence Planters
  - Herb Planters
  - Front Steps
- Plant chips sized by height and colored by bloom color
- "Sow this week" panel showing indoor/outdoor planting tasks
- Defaults to current week on page load

## Data Structure

```
data/
  2025/
    seeds.csv
  2026/
    seeds.csv    # Seed inventory with company links, colors, heights
    beds.csv     # Garden bed layouts and planting schedule
```

### Seeds CSV Columns
- Plant (Category, Type)
- Name, Seed Company, URL
- Color, Height (min/max)
- Sow Date, Days to Sprout
- Recommended (inside/outside)

### Beds CSV Structure
- Garden areas with Back/Middle/Front positions
- Weekly timeline columns for sow dates
- Plant assignments per location

## Local Development

```bash
python3 -m http.server 8000
```

Then open http://localhost:8000

## Deployment

Hosted via GitHub Pages. Push to main branch to deploy.
