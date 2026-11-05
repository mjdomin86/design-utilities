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
    const selects = ['search-workspace', 'op-workspace', 'backup-workspace', 'unused-workspace', 'jackson-workspace',
        'defaults-workspace']
        .map((id) => document.getElementById(id));
    const restoreWorkspaceSelect = document.getElementById('restore-workspace');
    try {
        const workspaces = await fetchJson('/api/workspaces');
        const options = workspaces
            .map((ws) => `<option value="${escapeHtml(ws.key)}">${escapeHtml(ws.name || ws.key)}</option>`)
            .join('');
        selects.forEach((select) => { select.innerHTML = options; });
        restoreWorkspaceSelect.innerHTML = '<option value="">(default workspace)</option>' + options;
        await Promise.all(APP_PICKERS.map(populateApps));
    } catch (e) {
        selects.forEach((select) => { select.innerHTML = '<option value="">(unavailable)</option>'; });
        restoreWorkspaceSelect.innerHTML = '<option value="">(unavailable)</option>';
        setStatus('search-status', `Could not load workspaces: ${e.message}`, true);
    }
}

const APP_PICKERS = [
    { workspaceId: 'op-workspace', appId: 'op-app', statusId: 'operations-status' },
    { workspaceId: 'defaults-workspace', appId: 'defaults-app', statusId: 'defaults-status' },
];

async function populateApps({ workspaceId, appId, statusId }) {
    const workspace = document.getElementById(workspaceId).value;
    const appSelect = document.getElementById(appId);
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
        setStatus(statusId, `Could not load apps: ${e.message}`, true);
    }
}

APP_PICKERS.forEach((picker) => {
    document.getElementById(picker.workspaceId).addEventListener('change', () => populateApps(picker));
});

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

// --- Restore ---
document.getElementById('restore-create-workspace').addEventListener('change', (event) => {
    const creating = event.target.checked;
    document.getElementById('restore-existing-workspace-row').style.display = creating ? 'none' : '';
    document.getElementById('restore-new-workspace-row').style.display = creating ? '' : 'none';
    document.getElementById('restore-new-workspace-name-row').style.display = creating ? '' : 'none';
});

document.getElementById('restore-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const createWorkspace = document.getElementById('restore-create-workspace').checked;
    const request = {
        sourceDirectory: document.getElementById('restore-source').value,
        createWorkspace,
        workspaceKey: createWorkspace
            ? document.getElementById('restore-new-workspace-key').value
            : document.getElementById('restore-workspace').value,
        workspaceName: createWorkspace ? document.getElementById('restore-new-workspace-name').value : null,
    };
    setStatus('restore-status', 'Restoring… this may take a while');
    document.getElementById('restore-results').innerHTML = '';
    try {
        const result = await fetchJson('/api/restores', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request),
        });
        setStatus('restore-status',
            `${result.successCount}/${result.totalApps} app(s) restored into workspace '${result.workspaceKey}'`
            + (result.workspaceCreated ? ' (created)' : '')
            + (result.failureCount ? ` — ${result.failureCount} failed` : ''),
            result.failureCount > 0);
        renderRestoreResults(result.apps);
    } catch (e) {
        setStatus('restore-status', e.message, true);
    }
});

function renderRestoreResults(apps) {
    const rows = apps.map((app) => `
        <tr class="${app.success ? '' : 'failed'}">
            <td><code>${escapeHtml(app.appKey)}</code></td>
            <td>${escapeHtml(app.appName || '')}</td>
            <td>${app.success ? `${app.fileName} (${app.sizeBytes} bytes)` : escapeHtml(app.error || 'failed')}</td>
        </tr>`).join('');
    document.getElementById('restore-results').innerHTML =
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

// --- Jackson migration ---
document.getElementById('jackson-scan-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const workspaceKey = document.getElementById('jackson-workspace').value;
    setStatus('jackson-scan-status', 'Scanning… this may take a while for large workspaces');
    document.getElementById('jackson-scan-results').innerHTML = '';
    document.getElementById('jackson-fix-form').style.display = 'none';
    setStatus('jackson-fix-status', '');
    document.getElementById('jackson-fix-results').innerHTML = '';
    try {
        const result = await fetchJson('/api/jackson-migration/scan', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ workspaceKey, modelTypes: [] }),
        });
        setStatus('jackson-scan-status',
            `${result.matches.length} model(s) with Jackson 2 references out of ${result.scannedModels} scanned`
            + (result.erroredModels ? ` (${result.erroredModels} could not be read)` : ''));
        renderJacksonScanResults(result.matches);
        document.getElementById('jackson-fix-form').style.display = result.matches.length ? '' : 'none';
    } catch (e) {
        setStatus('jackson-scan-status', e.message, true);
    }
});

function renderJacksonScanResults(matches) {
    if (!matches.length) {
        document.getElementById('jackson-scan-results').innerHTML = '';
        return;
    }
    const rows = matches.map((match) => `
        <tr>
            <td><input type="checkbox" class="jackson-select" data-model-type="${escapeHtml(match.modelType)}"
                       data-model-key="${escapeHtml(match.modelKey)}"></td>
            <td><span class="badge">${escapeHtml(match.modelType)}</span></td>
            <td>${escapeHtml(match.modelName || '')}</td>
            <td><code>${escapeHtml(match.modelKey)}</code></td>
            <td>${match.occurrences}</td>
            <td>${match.needsManualReview ? '<span class="badge badge-warning">review exceptions</span>' : ''}</td>
            <td>${match.apps.map((app) => escapeHtml(app.name || app.key)).join(', ')}</td>
        </tr>`).join('');
    document.getElementById('jackson-scan-results').innerHTML = `
        <table>
            <thead><tr>
                <th><input type="checkbox" id="jackson-select-all"></th>
                <th>Type</th><th>Name</th><th>Key</th><th>Occurrences</th><th>Flags</th><th>Apps</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>`;
    document.getElementById('jackson-select-all').addEventListener('change', (event) => {
        document.querySelectorAll('.jackson-select').forEach((cb) => { cb.checked = event.target.checked; });
    });
}

document.getElementById('jackson-fix-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const workspaceKey = document.getElementById('jackson-workspace').value;
    const models = Array.from(document.querySelectorAll('.jackson-select:checked'))
        .map((cb) => ({ modelType: cb.dataset.modelType, modelKey: cb.dataset.modelKey }));
    if (!models.length) {
        setStatus('jackson-fix-status', 'Select at least one model to fix.', true);
        return;
    }
    setStatus('jackson-fix-status', 'Fixing selected models…');
    document.getElementById('jackson-fix-results').innerHTML = '';
    try {
        const result = await fetchJson('/api/jackson-migration/fix', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ workspaceKey, models }),
        });
        setStatus('jackson-fix-status',
            `${result.successCount}/${result.totalModels} model(s) fixed`
            + (result.failureCount ? ` — ${result.failureCount} failed` : ''),
            result.failureCount > 0);
        renderJacksonFixResults(result.results);
    } catch (e) {
        setStatus('jackson-fix-status', e.message, true);
    }
});

function renderJacksonFixResults(results) {
    const rows = results.map((r) => `
        <tr class="${r.success ? '' : 'failed'}">
            <td><span class="badge">${escapeHtml(r.modelType)}</span></td>
            <td><code>${escapeHtml(r.modelKey)}</code></td>
            <td>${r.success ? `${r.occurrencesFixed} occurrence(s) fixed` : escapeHtml(r.error || 'failed')}</td>
        </tr>`).join('');
    document.getElementById('jackson-fix-results').innerHTML =
        `<table><thead><tr><th>Type</th><th>Key</th><th>Result</th></tr></thead><tbody>${rows}</tbody></table>`;
}

// --- Service default values ---
document.getElementById('defaults-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const appSelect = document.getElementById('defaults-app');
    const request = {
        workspaceKey: document.getElementById('defaults-workspace').value,
        appKey: appSelect.value,
        defaultValue: document.getElementById('defaults-value').value,
    };
    const appLabel = appSelect.selectedOptions[0] ? appSelect.selectedOptions[0].textContent : request.appKey;
    if (!window.confirm(`Add default value ${request.defaultValue} to the number output parameters of every `
        + `service model in '${appLabel}' and re-import the app into Flowable Design?`)) {
        return;
    }
    setStatus('defaults-status', 'Updating service models and re-importing the app…');
    document.getElementById('defaults-results').innerHTML = '';
    try {
        const result = await fetchJson('/api/service-default-values', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request),
        });
        setStatus('defaults-status',
            (result.imported
                ? `${result.changes.length} parameter(s) updated and app re-imported`
                : 'No number output parameters needed a default; app was not re-imported')
            + ` — ${result.serviceModelsScanned} service model(s) scanned`
            + (result.failures.length ? `, ${result.failures.length} could not be read` : ''),
            result.failures.length > 0);
        renderDefaultsResults(result);
    } catch (e) {
        setStatus('defaults-status', e.message, true);
    }
});

function renderDefaultsResults(result) {
    const changeRows = result.changes.map((change) => `
        <tr>
            <td>${escapeHtml(change.serviceModelName || '')} <code>${escapeHtml(change.serviceModelKey)}</code></td>
            <td>${escapeHtml(change.operationName || '')} <code>${escapeHtml(change.operationKey)}</code></td>
            <td><code>${escapeHtml(change.parameterName)}</code></td>
            <td>${escapeHtml(change.parameterType)}</td>
        </tr>`).join('');
    const failureRows = result.failures.map((failure) => `
        <tr class="failed">
            <td colspan="2"><code>${escapeHtml(failure.entryName)}</code></td>
            <td colspan="2">${escapeHtml(failure.error || 'could not be read')}</td>
        </tr>`).join('');
    document.getElementById('defaults-results').innerHTML = changeRows || failureRows
        ? `<table><thead><tr><th>Service model</th><th>Operation</th><th>Parameter</th><th>Type</th></tr></thead>
           <tbody>${changeRows}${failureRows}</tbody></table>`
        : '';
}

// --- OAuth2 token settings ---
function renderTokenStatus(status) {
    document.getElementById('token-mode').textContent = status.mode;
    if (!status.configured) {
        document.getElementById('token-current').textContent = 'no token configured';
        return;
    }
    if (!status.expiresAt) {
        document.getElementById('token-current').textContent = 'configured (expiry unknown)';
        return;
    }
    const expiresAt = new Date(status.expiresAt);
    document.getElementById('token-current').textContent = status.expired
        ? `expired at ${expiresAt.toLocaleString()}`
        : `valid until ${expiresAt.toLocaleString()}`;
}

async function loadTokenStatus() {
    try {
        renderTokenStatus(await fetchJson('/api/config/oauth2-token'));
    } catch (e) {
        setStatus('token-status', `Could not load token status: ${e.message}`, true);
    }
}

document.getElementById('token-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const tokenField = document.getElementById('token-value');
    setStatus('token-status', 'Saving…');
    try {
        const status = await fetchJson('/api/config/oauth2-token', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token: tokenField.value }),
        });
        renderTokenStatus(status);
        tokenField.value = '';
        setStatus('token-status', 'Token saved. Reloading workspaces…');
        await populateWorkspaces();
        setStatus('token-status', 'Token saved.');
    } catch (e) {
        setStatus('token-status', e.message, true);
    }
});

populateModelTypes();
populateWorkspaces();
loadTokenStatus();
