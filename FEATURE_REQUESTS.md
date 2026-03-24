# Feature Requests

## Open

### Bed Timeline View
A grid/timeline view showing plants in a bed across weeks.

**Phase 1 — Table grid:**
- Rows = plants, columns = weeks
- Each cell is empty or shows an event: sow date, transplant date (optional), harvest date range
- Color-coded indicators per event type

**Indoor vs. outdoor sowing:**
- Each plant in a bed should indicate whether it's sown indoors or direct-sown in the bed
- If sown indoors, it doesn't occupy hex cells until its transplant date (ties into timelapse and succession sowing FRs)
- Indoor sow date still appears in the timeline and TODO list as an action item, just not spatially in the bed view

**Phase 2 — Line plot with drag/drop:**
- Each row becomes a horizontal line plot
- Dots on the line represent events (sow, transplant, harvest start/end)
- Dots are draggable along the timeline to adjust dates interactively

### Add Seeds via URL (Web Scraping)
Allow users to add a seed to the seed bank by pasting a product URL (e.g. a Botanical Interests seed page).

- Scrape the page to extract relevant properties (name, days to maturity, spacing, etc.)
- Pre-populate a new seed entry with scraped data; user can review/edit before saving
- Should handle at least one well-known seed retailer to start (Botanical Interests), with potential to support others later

Open questions:
- Scraping client-side (in-app) vs. a small backend/serverless function?
- How to handle sites that block scraping or change their markup?
- Could fall back to AI-assisted extraction if structured data isn't cleanly available

### Seed Bank Spreadsheet View
A full expanded view of all seeds in the bank for easy bulk editing, similar to a Google Sheets-style table.

- All seeds as rows, all properties as columns (name, days to maturity, sow dates, spacing, etc.)
- Inline cell editing
- Could be a dedicated tab or view in the existing UI

### Plant Property Disambiguation: Inherent vs. Bed-Specific
Separate plant properties into two distinct categories:

- **Inherent properties** — belong to the plant species/variety in the seed bank (e.g. days to maturity, spacing, frost tolerance, height, companion plants, antagonists, water needs (drought tolerant / normal / heavy drinker / unknown)). Same across all uses of that plant.
- **Bed-specific properties** — belong to the plant *instance* within a particular bed (e.g. sow date, transplant date, starting size at planting, locked, position). Can vary per bed. See also: [Bed View Timelapse / Date Slider](#bed-view-timelapse--date-slider).

This affects data modeling, serialization, and how properties are displayed/edited in the UI (seed bank editor vs. bed editor).

### Garden / Zone Overview
A top-level view showing all beds laid out within a garden or zone.

- Visual overview of bed positions/layout within the space
- Click a bed to drill into the bed view
- Useful for spatial planning and quick navigation between beds

### Irrigation Planning & Watering Schedules
Model irrigation setups per bed and generate recommended watering schedules.

**Irrigation modes (user selects one):**
- **Sprayer** — user inputs number of sprayers and coverage radius; tool recommends optimal placement to cover the bed
- **Drip line** — recommend a drip line layout for the bed based on plant positions
- **Manual** — no irrigation hardware; generate a recommended hand-watering schedule

**Watering schedule:**
- Baseline schedule derived from plant water needs (inherent property) and local average rainfall
- Requires zip code input to pull avg monthly rainfall data
- Schedule adjusts per month based on expected precipitation vs. plant demand
- Time-aware: schedule should account for which plants are in the bed at a given point in the season (ties into succession sowing and timelapse FRs)

Open questions:
- Where does rainfall data come from — static lookup table by zip, or a live weather API?
- Is irrigation config per-bed or per-garden/zone?
- How do we handle plants with very different water needs sharing a bed?

### Structures (Trellises, etc.)
Some plants have a small ground footprint but grow vertically and require a support structure like a trellis. Need a way to model this — details TBD.

Open questions:
- Are structures inherent to the plant (always needs a trellis) or bed-specific (sometimes grown without)?
- How are structures represented visually in the bed view — as a separate overlay, an indicator on the hex, or something else?
- Do structures affect shadow casting or spacing scoring for neighboring plants?
- Can multiple plants share a structure?

### Advanced Scoring: Shadow Casting, Companion Planting & Antagonists
Expand the scoring engine to incorporate spatial, physical, and biological relationships between plants.

**Bed orientation & sun exposure:**
- Each bed has a cardinal facing direction (N/S/E/W or compound e.g. SE) — "face" being the bottom edge as laid out in the view
- Compass overlay shown in the bed view to make orientation visually clear
- Sun exposure tracked as hours of sun per day, varying by time of year (more in summer, less in shoulder seasons)
- Can be manually entered or derived from solar position data using zip code + bed orientation + known obstructions
- Granular version: hours per specific date pulled from a solar position API or static lat/lon lookup
- These properties also feed microclimate calibration — see [Garden Retrospective](#garden-retrospective--microclimate-calibration)

**Shadow casting:**
- Tall plants cast shadows on shorter neighbors depending on their relative position, bed orientation, and sun angle
- Penalize placements where a tall plant shades a sun-loving shorter plant
- Plant height lives as an inherent seed bank property
- Shadow direction and length should be derived from sun azimuth + elevation: `shadow_length = heightIn / tan(sun_elevation)`, offset in the direction opposite the sun azimuth
- `BedCanvas` draws a semi-transparent shadow polygon extending from each tall plant; a time-of-day slider could animate the shadow across the day
- New `HexGrid.shadowCells(row, col, heightIn, azimuthDeg, elevationDeg, gridRows, gridCols)` helper to enumerate affected cells
- Optional `shadeTolerant` flag on `PlantInstance` (or inferred from plant type) to differentiate scoring impact

**Companion planting:**
- Some plants benefit each other when placed nearby (e.g. the Three Sisters)
- Score bonus for known beneficial pairings in adjacent or nearby hex cells

**Antagonists:**
- Some plants inhibit each other (e.g. allelopathic relationships)
- Score penalty for known bad pairings in proximity

Plant height, companion, and antagonist relationships are inherent seed bank properties. See also: [Plant Property Disambiguation](#plant-property-disambiguation-inherent-vs-bed-specific).

Open questions:
- What other bed-level properties matter for microclimate (soil type, elevation, wind exposure)?
- Is sun exposure entered once per bed or managed as a seasonal curve?
- Which solar data source to use — static lookup by lat/lon, or a live API?

### Time-Aware Bed Scoring
The scoring/placement engine needs to become date-aware to support the timelapse view.

- Scores are computed per date (or date range), not once statically
- Hex cell overlap and spacing conflicts vary as plants grow, so compatibility scores change over time
- A bed that scores well in week 1 may score poorly in week 8 as plants fill out
- See also: [Bed View Timelapse / Date Slider](#bed-view-timelapse--date-slider), [Plant Property Disambiguation](#plant-property-disambiguation-inherent-vs-bed-specific)

### Succession Sowing / Multiple Plantings Per Cell
Support planting a different plant in a cell after the previous occupant has been harvested and cleared.

- A hex cell (or group of cells) can be reused across the season by different plants
- After a plant's harvest window ends, its cells become available again
- Planting UI needs to support placing a plant at a future date into a currently-occupied cell that will be free by then
- Scoring and timelapse views need to account for non-overlapping sequential plantings as valid, not conflicting

### Weekly TODO List & Planner vs. Active Mode
A task-oriented layer on top of the timeline to guide users through the season.

**Weekly TODO list (per bed or per garden):**
- Grouped action items for the current week:
  - Seeds to sow
  - Cold stratification / seed soaking needed
  - Seedlings to transplant
  - Seeds to thin
  - Seeds expected to sprout
  - Plants to harvest (expected)
- Color-coded borders on items indicating action type
- Tied to the timeline view — items are derived from planned dates

**Bed highlighting on hover:**
- Hovering a TODO item (e.g. a specific plant under "Seeds to Sow") grays out non-relevant plants in the bed view and highlights the hovered plant's hex cells
- Makes it immediately clear *where* in the bed the action needs to happen

**Planner Mode vs. Active Mode:**
- **Planner mode** — purely planning, no confirmations required, no tasks tracked
- **Active mode** — the plan is "live"; the app tracks what has and hasn't been done, notifies the user of upcoming and overdue tasks

**Task confirmation & schedule drift:**
- When a task is completed late (e.g. sowed 3/20 instead of planned 3/1), user updates the actual date
- Timeline shifts accordingly for that plant
- App flags downstream conflicts caused by the drift (e.g. harvest window now overlaps a succession planting in that cell)

**Graceful degradation for ignored tasks:**
- Overdue tasks shouldn't create a wall of blocking errors
- Options for the user: mark as done (with actual date), skip/remove the plant, or dismiss and proceed as if it happened on schedule
- Goal is a low-friction workflow that encourages actual use — failing gracefully is better than being cumbersome

Open questions:
- Is active mode per-bed, per-garden, or global?
- How does the app surface overdue tasks without being annoying (push notification, badge, dashboard widget)?
- Should dismissed/ignored tasks be logged for reference or silently dropped?

### Bed View Timelapse / Date Slider
A time-aware bed view showing plant growth over the season.

**Date slider:**
- A scrubable date slider in the bed view to preview the bed at any point in time
- Plants appear at their sow date (or transplant date) and are absent before it

**Growth visualization:**
- Plants start small (single hex cell) at sow date and grow week-over-week, expanding to their full hex footprint by maturity
- Hex size scales continuously between sow and full-size dates

**Planting at a specific date and size:**
- When adding a plant to a bed, specify the date it enters the bed and its starting size
- Supports both direct-sow (starts at 1 hex, grows from there) and transplanting (enters at a larger size on transplant date)
- Different plants can be added at different times, reflecting real staggered planting schedules

### App Icon & Productionization
Polish and package the app for easy distribution.

**App icon:**
- Replace default icon with something purpose-built and visually interesting for a garden planner

**Distribution:**
- Pre-packaged build downloadable directly from GitHub (e.g. via GitHub Releases)
- All user data stored locally (seed bank, garden maps, beds) — nothing uploaded or cloud-dependent
- Simple install experience (ideally just download and run)

**Updates:**
- Some mechanism to notify users of a newer version and make updating easy
- Details TBD — options include in-app update check against GitHub Releases, or a simple version banner

Open questions:
- What packaging format? (e.g. `.dmg` for macOS, installer for Windows, AppImage for Linux)
- Auto-update vs. manual download for new versions?
- Where does local data live and how is it migrated across versions?

### Garden Retrospective & Microclimate Calibration
A view that tracks actual vs. expected plant timelines over time, helping users calibrate their seed bank to their specific microclimate.

**Action-required vs. observed events (active mode distinction):**
- Some timeline events require user action (sow, transplant) — these are confirmed by the user with an actual date
- Others are observed/passive (germination, sprouting, harvest readiness) — the user can optionally log when these actually occurred vs. the expected date
- Logging observed events shouldn't necessarily shift the plan, but the data is captured for retrospective analysis

**Retrospective view (per season):**
- Shows all plants across all beds for the season with diffs between expected and actual dates/durations
- Highlights plants with a significant deviation (e.g. >5% shift from canonical seed bank values) as notable
- Gives a season-level summary of how the garden performed vs. plan

**Seed bank historical expando:**
- Each seed bank entry gets a collapsible history section showing performance across prior seasons
- Displays per-instance actuals alongside seed bank canonical values
- Shows averages across all recorded instances (e.g. "avg germination: 7 days vs. expected 14", "avg days to maturity: 52 vs. expected 60")

**Proactive update recommendations:**
- If a plant consistently deviates from its canonical seed bank values (e.g. matures 10 days early across 3+ seasons), the app proactively suggests updating the seed bank entry to reflect the user's microclimate
- User can accept the suggested update, dismiss, or edit manually

**Canonical vs. microclimate values:**
- Seed bank entries always retain their original canonical values (from seed packet, URL scrape, or manual entry) — these are never overwritten
- Microclimate-adjusted values are stored alongside as a separate layer, scoped to a garden or zone
- When planning a bed, the app uses microclimate values if available for that zone, falling back to canonical values otherwise
- Both values are visible in the seed bank — canonical as the baseline, microclimate as a personalized override
- This means the same plant can have different effective timelines depending on which garden/zone it's placed in

Open questions:
- Are retrospective actuals stored per bed-instance or rolled up globally per plant?
- What's the minimum number of seasons/instances before a recommendation is surfaced?

### Random Bed Generator (Onboarding / Sandbox)
A "Generate Random Bed" button to give new users an instant playground without needing to configure anything first.

- One-click generates a populated bed with a random assortment of plants from the seed bank (or a bundled default set if the seed bank is empty)
- Result should be a plausible, reasonably diverse bed — not just noise — so it feels like a real starting point
- Could offer a light prompt before generating (e.g. bed size, "mostly vegetables / flowers / mixed") or just generate with sensible defaults and let the user tweak
- Generated bed drops straight into the bed editor so the user can immediately start moving plants, locking things, re-running the optimizer, etc.

### Seed Tray Overlay in Bed View
Show plants currently in seed trays alongside the bed view so users can see what's coming soon and where it will land.

- A seed tray panel lists plants that are started but not yet in the bed (indoors, germinating, hardening off, etc.)
- Hovering or selecting a seed tray entry illuminates its planned placement cells in the bed canvas — showing the future footprint before the plant is physically transplanted
- Allocated tray plants are visually distinct from placed plants (e.g. dashed outline, ghost/semi-transparent hex fill)
- Ties into the timeline FR: a tray plant's transplant date determines when it transitions from "tray" to "placed" in the bed view
- Relates to the indoor vs. outdoor sowing distinction already noted in the Bed Timeline View FR

### GitHub Issue Skill for Bugs & Feature Requests
Create a Claude Code slash command (skill) to streamline formal bug and FR creation directly in GitHub instead of maintaining this markdown file manually.

- `/bug` and `/fr` (or a single `/issue`) skills that prompt for a title + description and open a GitHub issue with the appropriate label (`bug` or `enhancement`)
- Should pre-populate the issue body with a structured template (steps to reproduce / expected vs. actual for bugs; motivation / proposed behavior for FRs)
- This FEATURE_REQUESTS.md and any future bugs.md can then be retired in favour of GitHub Issues as the canonical tracker
- Example bug to file: when moving a plant toward the bed border, arrow keys get captured by the tab pane and switch tabs instead of moving the plant; the plant sometimes disappears when switching back to the tab

## In Progress

## Completed
