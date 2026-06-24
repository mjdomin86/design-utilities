const MODEL_TYPES = [
    'bpmn', 'cmmn', 'dmn', 'form', 'app', 'action', 'document', 'user', 'page', 'security',
    'dataObject', 'template', 'service', 'liquibase', 'channel', 'event', 'decisionService',
    'query', 'variableExtractor', 'sequence', 'dataDictionary', 'palette', 'sla',
    'dashboardComponent', 'plugin', 'pluginTask', 'agent', 'knowledgeBase', 'masterData',
];

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

async function fetchJson(url, options) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(body && body.detail ? body.detail : `Request failed (HTTP ${response.status})`);
    }
    return body;
}

function setStatus(id, message, isError) {
    const el = document.getElementById(id);
    el.textContent = message || '';
    el.classList.toggle('error', Boolean(isError));
}

// --- Tabs ---
document.querySelectorAll('.tab-button').forEach((button) => {
    button.addEventListener('click', () => {
        document.querySelectorAll('.tab-button').forEach((b) => b.classList.remove('active'));
        document.querySelectorAll('.tab').forEach((t) => t.classList.remove('active'));
        button.classList.add('active');
        document.getElementById(`tab-${button.dataset.tab}`).classList.add('active');
    });
});

// --- Initial data ---
function populateModelTypes() {
    document.getElementById('search-model-types').innerHTML = MODEL_TYPES
        .map((type) => `<option value="${type}" ${type === 'bpmn' ? 'selected' : ''}>${type}</option>`)
        .join('');
    document.getElementById('unused-model-types').innerHTML = MODEL_TYPES
        .map((type) => `<option value="${type}">${type}</option>`)
        .join('');
}

async function populateWorkspaces() {
    const selects = ['search-workspace', 'op-workspace', 'backup-workspace', 'unused-workspace']
        .map((id) => document.getElementById(id));
    try {
        const workspaces = await fetchJson('/api/workspaces');
        const options = workspaces
            .map((ws) => `<option value="${escapeHtml(ws.key)}">${escapeHtml(ws.name || ws.key)}</option>`)
            .join('');
        selects.forEach((select) => { select.innerHTML = options; });
        await populateApps();
    } catch (e) {
        selects.forEach((select) => { select.innerHTML = '<option value="">(unavailable)</option>'; });
        setStatus('search-status', `Could not load workspaces: ${e.message}`, true);
    }
}

async function populateApps() {
    const workspace = document.getElementById('op-workspace').value;
    const appSelect = document.getElementById('op-app');
    if (!workspace) {
        appSelect.innerHTML = '';
        return;
    }
    try {
        const apps = await fetchJson(`/api/workspaces/${encodeURIComponent(workspace)}/apps`);
        appSelect.innerHTML = apps
            .map((app) => `<option value="${escapeHtml(app.key)}">${escapeHtml(app.name || app.key)}</option>`)
            .join('');
    } catch (e) {
        appSelect.innerHTML = '';
        setStatus('operations-status', `Could not load apps: ${e.message}`, true);
    }
}

document.getElementById('op-workspace').addEventListener('change', populateApps);

// --- Model search ---
document.getElementById('search-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const modelTypes = Array.from(document.getElementById('search-model-types').selectedOptions)
        .map((option) => option.value);
    const request = {
        workspaceKey: document.getElementById('search-workspace').value,
        modelTypes,
        condition: document.getElementById('search-condition').value,
        term: document.getElementById('search-term').value,
        elementType: document.getElementById('search-element-type').value || null,
    };
    setStatus('search-status', 'Searching…');
    document.getElementById('search-results').innerHTML = '';
    try {
        const result = await fetchJson('/api/search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request),
        });
        setStatus('search-status',
            `${result.matches.length} matching model(s) out of ${result.scannedModels} scanned`
            + (result.erroredModels ? ` (${result.erroredModels} could not be read)` : ''));
        renderSearchResults(result.matches);
    } catch (e) {
        setStatus('search-status', e.message, true);
    }
});

function renderSearchResults(matches) {
    const container = document.getElementById('search-results');
    container.innerHTML = matches.map((match) => `
        <div class="result-card">
            <div class="result-title">
                <span class="badge">${escapeHtml(match.modelType)}</span>
                <strong>${escapeHtml(match.modelName || match.modelKey)}</strong>
                <code>${escapeHtml(match.modelKey)}</code>
            </div>
            ${renderApps(match.apps)}
            ${match.snippet ? `<pre>${escapeHtml(match.snippet)}</pre>` : ''}
            ${match.elements.length ? renderElements(match.elements) : ''}
        </div>`).join('');
}

function renderApps(apps) {
    if (!apps || !apps.length) {
        return '<p class="muted">App: none (not in any app)</p>';
    }
    const labels = apps
        .map((app) => `${escapeHtml(app.name || app.key)} (<code>${escapeHtml(app.key)}</code>)`)
        .join(', ');
    return `<p class="muted">App: ${labels}</p>`;
}

function renderElements(elements) {
    const rows = elements.map((el) => `
        <tr>
            <td><code>${escapeHtml(el.elementId)}</code></td>
            <td>${escapeHtml(el.elementName || '')}</td>
            <td>${escapeHtml(el.elementType)}${el.flowableType ? ` (${escapeHtml(el.flowableType)})` : ''}</td>
        </tr>`).join('');
    return `<table><thead><tr><th>Element id</th><th>Name</th><th>Type</th></tr></thead><tbody>${rows}</tbody></table>`;
}

// --- Operation lookup ---
document.getElementById('operations-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const workspace = document.getElementById('op-workspace').value;
    const app = document.getElementById('op-app').value;
    const method = document.getElementById('op-method').value;
    const path = document.getElementById('op-path').value;
    setStatus('operations-status', 'Searching…');
    document.getElementById('operations-results').innerHTML = '';
    try {
        const url = `/api/workspaces/${encodeURIComponent(workspace)}/apps/${encodeURIComponent(app)}`
            + `/service-operations?method=${encodeURIComponent(method)}&path=${encodeURIComponent(path)}`;
        const result = await fetchJson(url);
        setStatus('operations-status',
            `${result.matches.length} matching operation(s) in ${result.scannedServiceModels} service model(s)`);
        renderOperationResults(result.matches);
    } catch (e) {
        setStatus('operations-status', e.message, true);
    }
});

function renderOperationResults(matches) {
    const container = document.getElementById('operations-results');
    container.innerHTML = matches.map((match) => `
        <div class="result-card">
            <div class="result-title">
                <span class="badge">${escapeHtml(match.method)}</span>
                <code>${escapeHtml(match.url)}</code>
            </div>
            <p>Service model: <code>${escapeHtml(match.serviceModelKey)}</code>
               (${escapeHtml(match.serviceModelName || '')})<br>
               Operation: <code>${escapeHtml(match.operationKey)}</code>
               (${escapeHtml(match.operationName || '')})</p>
            ${renderParameters('Inputs', match.inputParameters)}
            ${renderParameters('Outputs', match.outputParameters)}
        </div>`).join('');
}

function renderParameters(title, parameters) {
    if (!parameters.length) {
        return `<p class="muted">${title}: none</p>`;
    }
    const rows = parameters.map((param) => `
        <tr>
            <td><code>${escapeHtml(param.name)}</code></td>
            <td>${escapeHtml(param.displayName || '')}</td>
            <td>${escapeHtml(param.type || '')}</td>
            <td>${param.required ? 'yes' : 'no'}</td>
        </tr>`).join('');
    return `<h4>${title}</h4>
        <table><thead><tr><th>Name</th><th>Display name</th><th>Type</th><th>Required</th></tr></thead>
        <tbody>${rows}</tbody></table>`;
}

// --- Backup ---
document.getElementById('backup-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const request = {
        workspaceKey: document.getElementById('backup-workspace').value,
        targetDirectory: document.getElementById('backup-target').value,
    };
    setStatus('backup-status', 'Backup running, this may take a while…');
    document.getElementById('backup-results').innerHTML = '';
    try {
        const result = await fetchJson('/api/backups', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request),
        });
        setStatus('backup-status',
            `Backed up ${result.successCount}/${result.totalApps} app(s) to ${result.directory}`,
            result.failureCount > 0);
        renderBackupResults(result.apps);
    } catch (e) {
        setStatus('backup-status', e.message, true);
    }
});

function renderBackupResults(apps) {
    const rows = apps.map((app) => `
        <tr class="${app.success ? '' : 'failed'}">
            <td><code>${escapeHtml(app.appKey)}</code></td>
            <td>${escapeHtml(app.appName || '')}</td>
            <td>${app.success ? `${app.fileName} (${app.sizeBytes} bytes)` : escapeHtml(app.error || 'failed')}</td>
        </tr>`).join('');
    document.getElementById('backup-results').innerHTML =
        `<table><thead><tr><th>App</th><th>Name</th><th>Result</th></tr></thead><tbody>${rows}</tbody></table>`;
}

// --- Unused models ---
document.getElementById('unused-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const workspace = document.getElementById('unused-workspace').value;
    const modelTypes = Array.from(document.getElementById('unused-model-types').selectedOptions)
        .map((option) => option.value);
    setStatus('unused-status', 'Scanning… this may take a while for large workspaces');
    document.getElementById('unused-results').innerHTML = '';
    try {
        const query = modelTypes.length
            ? '?' + modelTypes.map((type) => `modelTypes=${encodeURIComponent(type)}`).join('&')
            : '';
        const result = await fetchJson(
            `/api/workspaces/${encodeURIComponent(workspace)}/unused-models${query}`);
        setStatus('unused-status',
            `${result.unusedModels.length} unused model(s) out of ${result.scannedModels} scanned`
            + (result.erroredModels ? ` (${result.erroredModels} could not be checked)` : ''));
        renderUnusedResults(result.unusedModels);
    } catch (e) {
        setStatus('unused-status', e.message, true);
    }
});

function renderUnusedResults(models) {
    if (!models.length) {
        document.getElementById('unused-results').innerHTML = '';
        return;
    }
    const rows = models.map((model) => `
        <tr>
            <td><span class="badge">${escapeHtml(model.modelType)}</span></td>
            <td>${escapeHtml(model.modelName || '')}</td>
            <td><code>${escapeHtml(model.modelKey)}</code></td>
        </tr>`).join('');
    document.getElementById('unused-results').innerHTML =
        `<table><thead><tr><th>Type</th><th>Name</th><th>Key</th></tr></thead><tbody>${rows}</tbody></table>`;
}

populateModelTypes();
populateWorkspaces();
