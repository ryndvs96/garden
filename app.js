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
let currentData = [];

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

    renderTableHead();
    populateFilters();
    renderTable();
}

function init() {
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', () => loadYear(tab.dataset.year));
    });

    document.getElementById('search').addEventListener('input', renderTable);
    document.getElementById('plantType').addEventListener('change', renderTable);
    document.getElementById('allocatedOnly').addEventListener('change', renderTable);

    loadYear('2026');
}

init();
