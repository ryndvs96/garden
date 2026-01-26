const colorMap = {
    'red': '#dc3545',
    'yellow': '#ffc107',
    'orange': '#fd7e14',
    'pink': '#e83e8c',
    'purple': '#6f42c1',
    'blue': '#007bff',
    'white': '#f8f9fa',
    'green': '#28a745'
};

function splitPlant(value, index) {
    if (!value) return '-';
    const parts = value.split(',').map(p => p.trim());
    return parts[index] || '-';
}

function renderCategory(value) {
    return splitPlant(value, 0);
}

function renderType(value) {
    return splitPlant(value, 1);
}

const yearConfig = {
    '2026': {
        file: 'data/2026/seeds.csv',
        bedsFile: 'data/2026/beds.csv',
        hasBeds: true,
        bedsFormat: '2026',
        columns: [
            { key: 'Plant', label: 'Category', render: renderCategory },
            { key: 'Plant', label: 'Type', render: renderType },
            { key: 'Name', label: 'Name' },
            { key: 'Color', label: 'Color', render: renderColor },
            { key: 'Seed Company', label: 'Company', render: renderCompany },
            { key: 'Sow Date', label: 'Sow Date', class: 'sow-date' },
            { key: 'Days to Sprout', label: 'Days to Sprout' },
            { key: 'Height', label: 'Height' },
            { key: 'Recommended', label: 'Start' }
        ]
    },
    '2025': {
        file: 'data/2025/seeds.csv',
        bedsFile: 'data/2025/beds.csv',
        hasBeds: true,
        bedsFormat: '2025',
        columns: [
            { key: 'Plant', label: 'Category', render: renderCategory },
            { key: 'Plant', label: 'Type', render: renderType },
            { key: 'Name', label: 'Name' },
            { key: 'Seed Company', label: 'Company', render: renderCompany },
            { key: 'Location', label: 'Location' },
            { key: 'Days to maturity', label: 'Days to Maturity' },
            { key: 'Height', label: 'Height' },
            { key: 'Where to sow', label: 'Start' },
            { key: 'When to sow weeks w.r.t last frost', label: 'When to Sow' }
        ]
    }
};

let currentYear = '2026';
let currentView = 'seeds';
let currentData = [];
let bedsData = [];

function parseCSV(text) {
    const lines = text.trim().split('\n');
    const headers = parseCSVLine(lines[0]);
    return lines.slice(1).map(line => {
        const values = parseCSVLine(line);
        const obj = {};
        headers.forEach((h, i) => obj[h.trim()] = values[i] || '');
        return obj;
    });
}

function parseCSVLine(line) {
    const result = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
        const char = line[i];
        if (char === '"') {
            inQuotes = !inQuotes;
        } else if (char === ',' && !inQuotes) {
            result.push(current.trim());
            current = '';
        } else {
            current += char;
        }
    }
    result.push(current.trim());
    return result;
}

function getColorDots(colorStr) {
    if (!colorStr) return '';
    const colors = colorStr.split(',').map(c => c.trim().toLowerCase());
    return colors.map(c => {
        const hex = colorMap[c] || '#999';
        return `<span class="color-dot" style="background:${hex}" title="${c}"></span>`;
    }).join('');
}

function renderColor(value) {
    return getColorDots(value) || '-';
}

function renderCompany(value, row) {
    if (!value || value === '-') return '-';
    const url = row['URL'];
    if (url) {
        return `<a href="${url}" target="_blank" rel="noopener">${value}</a>`;
    }
    return value;
}

function renderTableHead() {
    const config = yearConfig[currentYear];
    const thead = document.getElementById('tableHead');
    thead.innerHTML = `<tr>${config.columns.map(col => `<th>${col.label}</th>`).join('')}</tr>`;
}

function renderTable() {
    const search = document.getElementById('search').value.toLowerCase();
    const plantType = document.getElementById('plantType').value;
    const allocatedOnly = document.getElementById('allocatedOnly').checked;
    const config = yearConfig[currentYear];

    const filtered = currentData.filter(row => {
        if (allocatedOnly && row['Allocated?'] !== 'TRUE') return false;
        if (plantType && getCategory(row['Plant']) !== plantType) return false;
        if (search) {
            const text = `${row['Plant']} ${row['Name']} ${row['Seed Company']}`.toLowerCase();
            if (!text.includes(search)) return false;
        }
        return true;
    });

    document.getElementById('stats').textContent = `Showing ${filtered.length} of ${currentData.length} plants`;

    const tbody = document.getElementById('tableBody');
    tbody.innerHTML = filtered.map(row => {
        const allocated = row['Allocated?'] === 'TRUE';
        const cells = config.columns.map(col => {
            const value = row[col.key] || '-';
            const content = col.render ? col.render(row[col.key], row) : value;
            const cls = col.class ? ` class="${col.class}"` : '';
            return `<td${cls}>${content}</td>`;
        }).join('');
        return `<tr class="${allocated ? '' : 'not-allocated'}">${cells}</tr>`;
    }).join('');
}

function getCategory(plant) {
    if (!plant) return '';
    return plant.split(',')[0].trim();
}

function parseBedsCsv(text, format) {
    const lines = text.trim().split('\n');
    let headers, dataStartRow, dateStartCol, plantTypeCol, nameCol, positionCol;

    if (format === '2026') {
        // 2026: Row 0 has week numbers, row 1 has actual headers
        headers = parseCSVLine(lines[1]);
        dataStartRow = 2;
        positionCol = 0;
        plantTypeCol = 3;
        nameCol = 4;
        dateStartCol = 5;
    } else {
        // 2025: Row 0 has headers directly
        headers = parseCSVLine(lines[0]);
        dataStartRow = 1;
        positionCol = -1; // No position column
        plantTypeCol = 2;
        nameCol = 3;
        dateStartCol = 4;
    }

    const dateColumns = headers.slice(dateStartCol).filter(h => h && h.match(/^\d+\/\d+$/));

    const areas = {};
    let currentArea = null;

    for (let i = dataStartRow; i < lines.length; i++) {
        const values = parseCSVLine(lines[i]);
        const position = positionCol >= 0 ? values[positionCol] : null;
        const plantType = values[plantTypeCol];
        const name = values[nameCol];

        // Check if this is an area header
        const isAreaHeader = format === '2026'
            ? (!position && !values[1] && !values[2] && plantType && !name)
            : (!values[0] && !values[1] && plantType && !name);

        if (isAreaHeader) {
            currentArea = plantType;
            if (!areas[currentArea]) {
                areas[currentArea] = { Back: [], Middle: [], Front: [], plants: [] };
            }
            continue;
        }

        // Stop processing at Legend section
        if (plantType === 'Legend') break;

        // Skip empty rows
        if (!plantType || !name) continue;

        // Parse lifecycle events from date columns
        const events = { sow: null, sprout: null, transplant: null, harvest: [], active: [] };

        for (let j = dateStartCol; j < values.length; j++) {
            const colIndex = j - dateStartCol;
            if (colIndex >= dateColumns.length) break;

            const cellValue = values[j];
            const weekDate = dateColumns[colIndex];

            if (!cellValue) continue;

            if (cellValue.match(/^\d+\/\d+$/)) {
                // It's a date - this is a sow date
                if (!events.sow) events.sow = weekDate;
            } else if (cellValue === 'Sp') {
                events.sprout = weekDate;
            } else if (cellValue === 'T') {
                events.transplant = weekDate;
            } else if (cellValue === 'H') {
                events.harvest.push(weekDate);
            } else if (cellValue === '--------') {
                events.active.push(weekDate);
            }
        }

        const plant = {
            position: position || 'General',
            plantType,
            name,
            sowDate: events.sow,
            sproutDate: events.sprout,
            transplantDate: events.transplant,
            harvestDates: events.harvest,
            activeDates: events.active,
            placed: format === '2026' ? values[1] === 'TRUE' : values[0] === 'TRUE',
            sown: format === '2026' ? values[2] === 'TRUE' : values[1] === 'TRUE'
        };

        if (currentArea && areas[currentArea]) {
            if (position && areas[currentArea][position]) {
                areas[currentArea][position].push(plant);
            } else {
                areas[currentArea].plants.push(plant);
            }
        }
    }

    return { areas, dateColumns, format };
}

function getWeeks() {
    if (!bedsData.dateColumns) return [];
    return bedsData.dateColumns;
}

let currentWeekIndex = 0;

function getEventCounts() {
    const sow = {};
    const harvest = {};
    if (!bedsData.areas) return { sow, harvest };

    Object.values(bedsData.areas).forEach(area => {
        const allPlants = [
            ...(area.Back || []),
            ...(area.Middle || []),
            ...(area.Front || []),
            ...(area.plants || [])
        ];
        allPlants.forEach(p => {
            if (p.sowDate) {
                sow[p.sowDate] = (sow[p.sowDate] || 0) + 1;
            }
            if (p.harvestDates) {
                p.harvestDates.forEach(h => {
                    harvest[h] = (harvest[h] || 0) + 1;
                });
            }
        });
    });
    return { sow, harvest };
}

// Keep for backwards compatibility
function getSowDateCounts() {
    return getEventCounts().sow;
}

function getPlantsForDate(date, eventType = 'sow') {
    const plants = [];
    if (!bedsData.areas) return plants;

    Object.values(bedsData.areas).forEach(area => {
        const allPlants = [
            ...(area.Back || []),
            ...(area.Middle || []),
            ...(area.Front || []),
            ...(area.plants || [])
        ];
        allPlants.forEach(p => {
            let matches = false;
            if (eventType === 'sow' && p.sowDate === date) {
                matches = true;
            } else if (eventType === 'harvest' && p.harvestDates && p.harvestDates.includes(date)) {
                matches = true;
            }
            if (matches && !plants.find(existing => existing.name === p.name)) {
                plants.push(p);
            }
        });
    });
    return plants;
}

function getNextEventDate(fromIndex, eventType = 'sow') {
    const weeks = getWeeks();
    const { sow: sowCounts, harvest: harvestCounts } = getEventCounts();
    const counts = eventType === 'sow' ? sowCounts : harvestCounts;

    for (let i = fromIndex + 1; i < weeks.length; i++) {
        if (counts[weeks[i]] > 0) {
            return { date: weeks[i], index: i };
        }
    }
    return null;
}

// Keep for backwards compatibility
function getNextSowDate(fromIndex) {
    return getNextEventDate(fromIndex, 'sow');
}

function getPlantSowLocation(plantName) {
    const seed = currentData.find(s => s['Name'] === plantName);
    if (!seed) return 'other';
    // Check both 2026 and 2025 column names
    const rec = (seed['Recommended'] || seed['Where to sow'] || '').toLowerCase();
    if (rec === 'inside') return 'indoor';
    if (rec === 'outside') return 'outdoor';
    return 'other';
}

function renderPlantChip(plant) {
    const chipStyle = getPlantChipStyle(plant.name);
    return `<div class="sow-info-plant" ${chipStyle}>
        <span class="plant-type">${splitPlant(plant.plantType, 1)}</span>
        ${plant.name}
    </div>`;
}

function renderSowSection(plants, showLocation = true) {
    if (plants.length === 0) return '';

    if (!showLocation) {
        return `<div class="sow-info-section"><div class="sow-info-plants">${plants.map(renderPlantChip).join('')}</div></div>`;
    }

    const indoor = plants.filter(p => getPlantSowLocation(p.name) === 'indoor');
    const outdoor = plants.filter(p => getPlantSowLocation(p.name) === 'outdoor');
    const other = plants.filter(p => getPlantSowLocation(p.name) === 'other');

    let sections = '';

    if (indoor.length > 0) {
        sections += `
            <div class="sow-info-section">
                <div class="sow-info-label">Indoor</div>
                <div class="sow-info-plants">${indoor.map(renderPlantChip).join('')}</div>
            </div>
        `;
    }

    if (outdoor.length > 0) {
        sections += `
            <div class="sow-info-section">
                <div class="sow-info-label">Outdoor</div>
                <div class="sow-info-plants">${outdoor.map(renderPlantChip).join('')}</div>
            </div>
        `;
    }

    if (other.length > 0) {
        sections += `
            <div class="sow-info-section">
                <div class="sow-info-label">Either</div>
                <div class="sow-info-plants">${other.map(renderPlantChip).join('')}</div>
            </div>
        `;
    }

    return sections;
}

function renderSowInfo() {
    const sowInfo = document.getElementById('sowInfo');
    const weeks = getWeeks();
    const currentDate = weeks[currentWeekIndex];

    const sowPlants = getPlantsForDate(currentDate, 'sow');
    const harvestPlants = getPlantsForDate(currentDate, 'harvest');

    let html = '';

    // Show sowing info
    if (sowPlants.length > 0) {
        html += `
            <div class="sow-info">
                <div class="sow-info-header">Sow this week <span class="date">(${currentDate})</span></div>
                ${renderSowSection(sowPlants, true)}
            </div>
        `;
    }

    // Show harvest info
    if (harvestPlants.length > 0) {
        html += `
            <div class="sow-info harvest">
                <div class="sow-info-header">Harvest this week <span class="date">(${currentDate})</span></div>
                ${renderSowSection(harvestPlants, false)}
            </div>
        `;
    }

    // Show coming up next if nothing this week
    if (sowPlants.length === 0 && harvestPlants.length === 0) {
        const nextSow = getNextEventDate(currentWeekIndex, 'sow');
        const nextHarvest = getNextEventDate(currentWeekIndex, 'harvest');

        if (nextSow || nextHarvest) {
            // Show whichever comes first, or both if same week
            if (nextSow) {
                const nextPlants = getPlantsForDate(nextSow.date, 'sow');
                html += `
                    <div class="sow-info upcoming">
                        <div class="sow-info-header">Coming up: Sow <span class="date">(${nextSow.date})</span></div>
                        ${renderSowSection(nextPlants, true)}
                    </div>
                `;
            }

            if (nextHarvest && (!nextSow || nextHarvest.index !== nextSow.index)) {
                const nextPlants = getPlantsForDate(nextHarvest.date, 'harvest');
                html += `
                    <div class="sow-info harvest upcoming">
                        <div class="sow-info-header">Coming up: Harvest <span class="date">(${nextHarvest.date})</span></div>
                        ${renderSowSection(nextPlants, false)}
                    </div>
                `;
            }
        } else {
            html = `<div class="sow-info"><div class="sow-info-header">No more events this season</div></div>`;
        }
    }

    sowInfo.innerHTML = html;
}

function setWeekIndex(index) {
    const weeks = getWeeks();
    currentWeekIndex = Math.max(0, Math.min(weeks.length - 1, index));

    document.getElementById('timelineCurrent').textContent = weeks[currentWeekIndex];

    document.querySelectorAll('.timeline-week').forEach((el, i) => {
        el.classList.toggle('active', i === currentWeekIndex);
    });

    renderSowInfo();
    renderBeds();
}

function findCurrentWeekIndex(weeks) {
    const now = new Date();
    const currentYear = now.getFullYear();

    // Parse weeks and find closest one to today
    let closestIndex = 0;
    let closestDiff = Infinity;

    weeks.forEach((week, i) => {
        const [month, day] = week.split('/').map(Number);
        const weekDate = new Date(currentYear, month - 1, day);
        const diff = Math.abs(now - weekDate);

        if (diff < closestDiff) {
            closestDiff = diff;
            closestIndex = i;
        }
    });

    return closestIndex;
}

function renderWeekSelector() {
    const timeline = document.getElementById('timeline');
    const weeks = getWeeks();
    const { sow: sowCounts, harvest: harvestCounts } = getEventCounts();
    const maxSow = Math.max(...Object.values(sowCounts), 1);
    const maxHarvest = Math.max(...Object.values(harvestCounts), 1);

    currentWeekIndex = findCurrentWeekIndex(weeks);
    document.getElementById('timelineCurrent').textContent = weeks[currentWeekIndex];

    timeline.innerHTML = weeks.map((week, i) => {
        const sowCount = sowCounts[week] || 0;
        const harvestCount = harvestCounts[week] || 0;
        const hasSow = sowCount > 0;
        const hasHarvest = harvestCount > 0;

        // Bar height based on count (min 4px, max 40px)
        const barHeight = hasSow || hasHarvest
            ? 8 + (Math.max(sowCount / maxSow, harvestCount / maxHarvest)) * 32
            : 4;

        // Determine bar class
        let barClass = 'has-event';
        if (hasSow && hasHarvest) barClass = 'has-both';
        else if (hasSow) barClass = 'has-sow';
        else if (hasHarvest) barClass = 'has-harvest';
        else barClass = '';

        // Show label for event dates or month starts
        const [month, day] = week.split('/');
        const isMonthStart = parseInt(day) <= 7;
        const showLabel = hasSow || hasHarvest || isMonthStart;

        return `<div class="timeline-week${barClass ? ' ' + barClass : ''}${i === currentWeekIndex ? ' active' : ''}" data-index="${i}">
            <div class="timeline-bar" style="height: ${barHeight}px"></div>
            ${showLabel ? `<span class="timeline-label">${week}</span>` : ''}
        </div>`;
    }).join('');

    renderSowInfo();

    // Click handler
    timeline.addEventListener('click', (e) => {
        const weekEl = e.target.closest('.timeline-week');
        if (weekEl) {
            setWeekIndex(parseInt(weekEl.dataset.index, 10));
        }
    });

    // Drag handler
    let isDragging = false;

    timeline.addEventListener('mousedown', (e) => {
        isDragging = true;
        handleDrag(e);
    });

    document.addEventListener('mousemove', (e) => {
        if (isDragging) handleDrag(e);
    });

    document.addEventListener('mouseup', () => {
        isDragging = false;
    });

    function handleDrag(e) {
        const rect = timeline.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const percent = Math.max(0, Math.min(1, x / rect.width));
        const index = Math.round(percent * (weeks.length - 1));
        setWeekIndex(index);
    }
}

function getPlantColor(plantName) {
    // Look up plant in seeds data
    const seed = currentData.find(s => s['Name'] === plantName);
    if (!seed || !seed['Color']) return null;

    const colors = seed['Color'].split(',').map(c => c.trim().toLowerCase());
    const hexColors = colors.map(c => colorMap[c]).filter(Boolean);

    if (hexColors.length === 0) return null;

    // Pick a random color if multiple
    const randomIndex = Math.floor(Math.random() * hexColors.length);
    return hexColors[randomIndex];
}

function getPlantHeight(plantName) {
    const seed = currentData.find(s => s['Name'] === plantName);
    if (!seed) return null;

    const minH = parseInt(seed['Height (min)'], 10);
    const maxH = parseInt(seed['Height (max)'], 10);

    if (isNaN(minH) || isNaN(maxH)) return null;
    return (minH + maxH) / 2;
}

function getPlantChipStyle(plantName) {
    const color = getPlantColor(plantName);
    const avgHeight = getPlantHeight(plantName);

    let styles = [];

    if (color) {
        styles.push(`background: ${color}40`);
        styles.push(`border-color: ${color}`);
    }

    if (avgHeight) {
        // Scale height: 6" -> 30px, 108" -> 120px
        const minPx = 30;
        const maxPx = 120;
        const minInches = 6;
        const maxInches = 108;
        const scaledHeight = minPx + ((avgHeight - minInches) / (maxInches - minInches)) * (maxPx - minPx);
        const clampedHeight = Math.max(minPx, Math.min(maxPx, scaledHeight));
        styles.push(`height: ${Math.round(clampedHeight)}px`);
        styles.push(`display: inline-flex`);
        styles.push(`flex-direction: column`);
        styles.push(`justify-content: center`);
    }

    if (styles.length === 0) return '';
    return `style="${styles.join('; ')}"`;
}

function renderBeds() {
    const layout = document.getElementById('gardenLayout');
    const weeks = getWeeks();
    const weekIndex = currentWeekIndex;

    // Get area order based on what's actually in the data
    const areaOrders = {
        '2026': ['Right Raised Bed', 'Left Raised Bed', 'Front Yard', 'Fence Planters', 'Herb Planters', 'Front Steps'],
        '2025': ['Right Raised Bed', 'Left Raised Bed', 'Grow Bags 15G', 'Grow Bags 5G', 'Fence Planters', 'Herb Planters', 'Front Steps']
    };
    const fullWidthAreas = ['Right Raised Bed', 'Left Raised Bed', 'Grow Bags 15G'];

    // Use available areas from data, falling back to known order
    const knownOrder = areaOrders[bedsData.format] || areaOrders['2026'];
    const areaOrder = knownOrder.filter(name => bedsData.areas[name]);

    layout.innerHTML = areaOrder.map(areaName => {
        const area = bedsData.areas[areaName];
        if (!area) return '';

        const positions = ['Back', 'Middle', 'Front'];
        const hasPositions = positions.some(p => area[p] && area[p].length > 0);

        let content = '';
        if (hasPositions) {
            content = positions.map(pos => {
                const plants = area[pos] || [];
                if (plants.length === 0) return '';

                const plantHtml = plants.filter(p => {
                    // Show plant if its sow date is at or before selected week
                    if (!p.sowDate) return true;
                    const sowIndex = weeks.indexOf(p.sowDate);
                    return sowIndex !== -1 && sowIndex <= weekIndex;
                }).map(p => {
                    const chipStyle = getPlantChipStyle(p.name);
                    return `<div class="bed-plant" ${chipStyle}>
                        <span class="plant-type">${splitPlant(p.plantType, 1)}</span>
                        ${p.name}
                    </div>`;
                }).join('');

                if (!plantHtml) return '';

                return `<div class="bed-section">
                    <div class="bed-section-title">${pos}</div>
                    <div class="bed-plants">${plantHtml}</div>
                </div>`;
            }).join('');
        } else if (area.plants && area.plants.length > 0) {
            const plantHtml = area.plants.filter(p => {
                if (!p.sowDate) return true;
                const sowIndex = weeks.indexOf(p.sowDate);
                return sowIndex !== -1 && sowIndex <= weekIndex;
            }).map(p => {
                const chipStyle = getPlantChipStyle(p.name);
                return `<div class="bed-plant" ${chipStyle}>
                    <span class="plant-type">${splitPlant(p.plantType, 1)}</span>
                    ${p.name}
                </div>`;
            }).join('');

            content = `<div class="bed-section">
                <div class="bed-plants">${plantHtml || '<em style="color:#999;font-size:12px">No plants yet</em>'}</div>
            </div>`;
        }

        if (!content) {
            content = `<div class="bed-section">
                <div class="bed-plants"><em style="color:#999;font-size:12px">No plants yet</em></div>
            </div>`;
        }

        const isFullWidth = fullWidthAreas.includes(areaName);
        return `<div class="bed-area${isFullWidth ? ' full-width' : ''}">
            <h3>${areaName}</h3>
            ${content}
        </div>`;
    }).join('');
}

function switchView(view) {
    currentView = view;
    document.querySelectorAll('.sub-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.view === view);
    });

    document.getElementById('seedsView').classList.toggle('hidden', view !== 'seeds');
    document.getElementById('bedsView').classList.toggle('hidden', view !== 'beds');

    if (view === 'beds') {
        renderBeds();
    }
}

function populateFilters() {
    const categories = [...new Set(currentData.map(r => getCategory(r['Plant'])).filter(Boolean))].sort();

    const typeSelect = document.getElementById('plantType');
    typeSelect.innerHTML = '<option value="">All Categories</option>';
    categories.forEach(t => {
        const opt = document.createElement('option');
        opt.value = t;
        opt.textContent = t;
        typeSelect.appendChild(opt);
    });
}

async function loadYear(year) {
    currentYear = year;
    const config = yearConfig[year];

    const response = await fetch(config.file);
    const text = await response.text();
    currentData = parseCSV(text);

    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.year === year);
    });

    // Show/hide sub-tabs based on year
    const subTabs = document.getElementById('subTabs');
    if (config.hasBeds) {
        subTabs.classList.remove('hidden');

        // Load beds data
        const bedsResponse = await fetch(config.bedsFile);
        const bedsText = await bedsResponse.text();
        bedsData = parseBedsCsv(bedsText, config.bedsFormat);
        renderWeekSelector();
    } else {
        subTabs.classList.add('hidden');
        // Switch to seeds view
        currentView = 'seeds';
        document.getElementById('seedsView').classList.remove('hidden');
        document.getElementById('bedsView').classList.add('hidden');
    }

    renderTableHead();
    populateFilters();
    renderTable();

    if (config.hasBeds && currentView === 'beds') {
        renderBeds();
    }
}

function init() {
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', () => loadYear(tab.dataset.year));
    });

    document.querySelectorAll('.sub-tab').forEach(tab => {
        tab.addEventListener('click', () => switchView(tab.dataset.view));
    });

    document.getElementById('search').addEventListener('input', renderTable);
    document.getElementById('plantType').addEventListener('change', renderTable);
    document.getElementById('allocatedOnly').addEventListener('change', renderTable);

    loadYear('2026');
}

init();
