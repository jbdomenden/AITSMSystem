const LAN_AUTO_REFRESH_VISIBLE_MS = 10 * 1000;
const LAN_AUTO_REFRESH_HIDDEN_MS = 30 * 1000;
let monitoringAutoRefreshTimer = null;
let monitoringRefreshInFlight = false;
let monitoringLastUpdatedAt = null;
let editingDeviceId = null;
let deviceRegistry = [];

function statusBadge(status) {
  const s = String(status || '').toLowerCase();
  if (s === 'critical') return 'open';
  if (s === 'high risk') return 'warning';
  if (s === 'offline' || s === 'unavailable' || s === 'unreachable') return 'warning';
  return 'resolved';
}

function setStatusField(status) {
  const normalized = (status || 'Unreachable').trim() || 'Unreachable';
  const hidden = document.getElementById('status');
  const display = document.getElementById('statusDisplay');
  if (hidden) hidden.value = normalized;
  if (display) display.value = normalized;
}

function formatLastSeen(value) {
  const date = new Date(value || '');
  return Number.isNaN(date.getTime()) ? (value || '-') : date.toLocaleString();
}

function formatRelativeTime(value) {
  if (!value) return 'Waiting for update…';
  const diffMs = Date.now() - value.getTime();
  if (diffMs < 5000) return 'Updated just now';
  const seconds = Math.round(diffMs / 1000);
  if (seconds < 60) return `Updated ${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  return `Updated ${minutes}m ago`;
}

function updateMonitoringLiveStatus(state = 'idle', message) {
  const chip = document.getElementById('monitorLiveStatus');
  const label = document.getElementById('monitorLiveStatusText');
  if (!chip || !label) return;

  chip.dataset.state = state;
  if (message) {
    label.textContent = message;
    return;
  }

  if (state === 'loading') {
    label.textContent = 'Refreshing telemetry…';
    return;
  }

  if (state === 'error') {
    label.textContent = 'Telemetry refresh failed';
    return;
  }

  label.textContent = formatRelativeTime(monitoringLastUpdatedAt);
}

function telemetryBadge(device) {
  const label = device.telemetryAvailable ? (device.telemetrySourceType || 'Telemetry') : 'Unavailable';
  const tone = device.telemetryAvailable ? 'resolved' : 'warning';
  return `<span class='badge ${tone} monitor-telemetry-badge' title='${label}'>${label}</span>`;
}

function renderMonitorSummary(summary) {
  const wrap = document.getElementById('monitorSummary');
  if (!wrap) return;

  const host = summary?.hostTelemetry || {};
  const chips = [
    ['Discovered Devices', summary?.totalDiscovered ?? 0],
    ['Monitored Devices', summary?.monitoredDevices ?? 0],
    ['Telemetry Available', summary?.telemetryAvailableDevices ?? 0],
    ['Host CPU', `${Number(host.cpuUsagePercent ?? 0).toFixed(1)}%`]
  ];

  wrap.innerHTML = chips.map(([label, value]) => `<div class='kpi-chip'><div class='kpi-label'>${label}</div><div class='kpi-value'>${value}</div></div>`).join('');
}

function renderMonitoringHealthPanel(summary, devices) {
  const panel = document.getElementById('monitorHealthPanel');
  if (!panel) return;

  const host = summary?.hostTelemetry;
  if (!host) {
    panel.innerHTML = `<div class='empty-state'><h3>Monitoring unavailable</h3><p>Unable to load host telemetry right now.</p></div>`;
    return;
  }

  const available = devices.filter(d => d.telemetryAvailable).length;
  panel.innerHTML = `
    <div class='insight-grid monitor-health-grid'>
      <div class='insight-item'><div class='insight-label'>Host</div><div class='insight-value monitor-text-wrap'>${host.hostname}</div></div>
      <div class='insight-item'><div class='insight-label'>Host Memory</div><div class='insight-value'>${Number(host.memoryUsagePercent ?? 0).toFixed(1)}%</div></div>
      <div class='insight-item'><div class='insight-label'>Telemetry coverage</div><div class='insight-value'>${available}/${devices.length}</div></div>
      <div class='insight-item'><div class='insight-label'>Last updated</div><div class='insight-value monitor-text-wrap monitor-updated-at'>${formatLastSeen(summary.timestamp || host.timestamp || monitoringLastUpdatedAt)}</div></div>
    </div>`;
}

function renderMonitorCards(devices) {
  const wrap = document.getElementById('monitorCards');
  if (!wrap) return;

  if (!devices.length) {
    wrap.innerHTML = `<div class='empty-state'><h3>No LAN devices found</h3><p>Run discovery refresh or wait for LAN telemetry ingestion from clients.</p><button class='btn btn-primary' onclick='refreshDiscovery()'>Refresh discovery</button></div>`;
    return;
  }

  wrap.innerHTML = devices.map(d => `
    <article class='card monitor-device-card'>
      <div class='card-head monitor-device-head'>
        <h3 class='section-title monitor-device-title' title='${d.hostname || d.ipAddress}'>${d.hostname || d.ipAddress}</h3>
        ${telemetryBadge(d)}
      </div>
      <div class='small monitor-device-meta'>
        <span class='monitor-text-wrap'>${d.ipAddress || '-'}</span>
        <span class='monitor-meta-separator' aria-hidden='true'>•</span>
        <span>Reachable: ${d.reachable ? 'Yes' : 'No'}</span>
      </div>
      <div class='insight-grid monitor-device-stats'>
        <div class='insight-item'><div class='insight-label'>CPU</div><div class='insight-value'>${d.cpuUsagePercent != null ? `${Number(d.cpuUsagePercent).toFixed(1)}%` : 'N/A'}</div></div>
        <div class='insight-item'><div class='insight-label'>Memory</div><div class='insight-value'>${d.memoryUsagePercent != null ? `${Number(d.memoryUsagePercent).toFixed(1)}%` : 'N/A'}</div></div>
      </div>
    </article>`).join('');
}

function renderMonitorTable(devices) {
  const rows = document.getElementById('monitorTableRows');
  if (!rows) return;

  if (!devices.length) {
    rows.innerHTML = `<tr><td colspan='7' class='small'>No LAN devices discovered yet.</td></tr>`;
    return;
  }

  rows.innerHTML = devices.map(d => `<tr>
    <td>${d.hostname || '-'}</td>
    <td>${d.ipAddress}</td>
    <td>${d.telemetrySourceType || '-'}</td>
    <td>${d.reachable ? 'Online' : 'Offline'}</td>
    <td>${d.cpuUsagePercent != null ? `${Number(d.cpuUsagePercent).toFixed(1)}%` : '-'}</td>
    <td>${d.memoryUsagePercent != null ? `${Number(d.memoryUsagePercent).toFixed(1)}%` : '-'}</td>
    <td><span class='badge ${statusBadge(d.telemetryAvailable ? 'online' : 'unavailable')}'>${d.telemetryAvailable ? 'Telemetry' : 'No telemetry'}</span></td>
  </tr>`).join('');
}

function renderLanIpSuggestions(devices) {
  const suggestions = document.getElementById('lanIpSuggestions');
  if (!suggestions) return;

  const uniqueIps = [...new Set((devices || []).map((device) => (device.ipAddress || '').trim()).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }));

  suggestions.innerHTML = uniqueIps.map((ip) => `<option value="${ip}"></option>`).join('');
}

function setRefreshDiscoveryBusy(isBusy) {
  const button = document.getElementById('refreshDiscoveryBtn');
  if (!button) return;
  button.disabled = isBusy;
  button.textContent = isBusy ? 'Refreshing…' : 'Refresh Discovery';
}

async function refreshDiscovery() {
  setRefreshDiscoveryBusy(true);
  try {
    const res = await fetch('/api/monitoring/refresh-discovery', { method: 'POST', headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) {
      alert(data.error || 'Failed to refresh discovery');
      return;
    }
    await loadMonitoring({ force: true, source: 'discovery' });
  } finally {
    setRefreshDiscoveryBusy(false);
  }
}

function scheduleMonitoringAutoRefresh() {
  stopMonitoringAutoRefresh();
  const interval = document.hidden ? LAN_AUTO_REFRESH_HIDDEN_MS : LAN_AUTO_REFRESH_VISIBLE_MS;
  monitoringAutoRefreshTimer = window.setInterval(() => {
    loadMonitoring({ source: 'auto' });
    loadDevices();
  }, interval);
}

async function loadMonitoring({ force = false, source = 'manual' } = {}) {
  if (monitoringRefreshInFlight && !force) return;
  monitoringRefreshInFlight = true;
  updateMonitoringLiveStatus('loading');

  try {
    const [summaryRes, devicesRes] = await Promise.all([
      fetch('/api/monitoring/summary', { headers: authHeaders() }),
      fetch('/api/monitoring/lan-devices', { headers: authHeaders() })
    ]);

    const summary = await summaryRes.json();
    const devices = await devicesRes.json();
    const safeDevices = Array.isArray(devices) ? devices : [];

    renderMonitorSummary(summaryRes.ok ? summary : null);
    renderMonitoringHealthPanel(summaryRes.ok ? summary : null, safeDevices);
    renderMonitorCards(safeDevices);
    renderMonitorTable(safeDevices);
    renderLanIpSuggestions(safeDevices);

    monitoringLastUpdatedAt = new Date();
    updateMonitoringLiveStatus('live', source === 'discovery' ? 'Discovery refreshed just now' : undefined);
  } catch {
    updateMonitoringLiveStatus('error');
  } finally {
    monitoringRefreshInFlight = false;
  }
}

async function autoFillDeviceContextByIp() {
  const ipInput = document.getElementById('ipAddress');
  const deviceInput = document.getElementById('deviceName');
  const assignedInput = document.getElementById('assignedUser');
  if (!ipInput || !deviceInput || !assignedInput) return;

  const ip = (ipInput.value || '').trim();
  if (!ip) return;

  try {
    const res = await fetch(`/api/devices/ip-lookup?ip=${encodeURIComponent(ip)}`, { headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) {
      setStatusField('Unreachable');
      return;
    }

    if (data.deviceName) deviceInput.value = data.deviceName;
    if (!assignedInput.value.trim() && data.assignedUser) assignedInput.value = data.assignedUser;
    setStatusField(data.suggestedStatus);
  } catch {
    setStatusField('Unreachable');
  }
}

function seedAssignedUserFromSession() {
  const assignedInput = document.getElementById('assignedUser');
  if (!assignedInput) return;
  if (assignedInput.value.trim()) return;
  const fullName = (localStorage.getItem('fullName') || '').trim();
  const email = (localStorage.getItem('email') || '').trim();
  assignedInput.value = fullName || email;
}

async function refreshAssetConnections() {
  const res = await fetch('/api/devices/sync-from-monitoring', { method: 'POST', headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to refresh device connections');
  await Promise.all([loadDevices(), loadMonitoring({ force: true, source: 'manual' })]);
  alert(data.message || 'Asset connections refreshed');
}

function bindAssetAutoFill() {
  const ipInput = document.getElementById('ipAddress');
  if (!ipInput || ipInput.dataset.boundAutofill === '1') return;
  ipInput.dataset.boundAutofill = '1';
  ipInput.addEventListener('blur', autoFillDeviceContextByIp);
  ipInput.addEventListener('change', autoFillDeviceContextByIp);
  ipInput.addEventListener('keydown', async (event) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    await autoFillDeviceContextByIp();
  });
}

function currentDeviceFormBody() {
  return {
    deviceName: (document.getElementById('deviceName')?.value || '').trim(),
    ipAddress: (document.getElementById('ipAddress')?.value || '').trim(),
    department: (document.getElementById('department')?.value || '').trim(),
    assignedUser: (document.getElementById('assignedUser')?.value || '').trim(),
    status: document.getElementById('status')?.value || 'Online'
  };
}

function resetDeviceForm() {
  editingDeviceId = null;
  const registerButton = document.querySelector("button[onclick='registerDevice()']");
  const cancelButton = document.getElementById('cancelDeviceEditBtn');
  if (registerButton) registerButton.textContent = 'Register Device';
  cancelButton?.classList.add('hidden');
  ['deviceName', 'ipAddress', 'department', 'assignedUser'].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  const statusEl = document.getElementById('status');
  if (statusEl) statusEl.value = 'Unreachable';
  setStatusField('Unreachable');
  seedAssignedUserFromSession();
}

function startEditDevice(device) {
  editingDeviceId = device.id;
  const registerButton = document.querySelector("button[onclick='registerDevice()']");
  const cancelButton = document.getElementById('cancelDeviceEditBtn');
  if (registerButton) registerButton.textContent = 'Save Changes';
  cancelButton?.classList.remove('hidden');

  document.getElementById('deviceName').value = device.deviceName || '';
  document.getElementById('ipAddress').value = device.ipAddress || '';
  document.getElementById('department').value = device.department || '';
  document.getElementById('assignedUser').value = device.assignedUser || '';
  setStatusField(device.status || 'Unreachable');
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function startEditDeviceById(id) {
  const device = deviceRegistry.find((entry) => entry.id === id);
  if (!device) {
    alert('Unable to load the selected device.');
    return;
  }
  startEditDevice(device);
}

async function deleteDevice(id, label) {
  const confirmed = window.confirm(`Delete asset "${label || `#${id}`}"?`);
  if (!confirmed) return;

  const res = await fetch(`/api/devices/${id}`, { method: 'DELETE', headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Failed to delete device.');

  if (editingDeviceId === id) resetDeviceForm();
  await Promise.all([loadDevices(), loadMonitoring({ force: true, source: 'manual' })]);
  alert(data.message || 'Device deleted');
}

async function registerDevice() {
  const deviceNameEl = document.getElementById('deviceName');
  const ipAddressEl = document.getElementById('ipAddress');

  if (!(deviceNameEl?.value || '').trim() && (ipAddressEl?.value || '').trim()) {
    await autoFillDeviceContextByIp();
  }

  const body = currentDeviceFormBody();

  if (!body.deviceName || !body.ipAddress || !body.department || !body.assignedUser || !body.status) {
    return alert('All device fields are required.');
  }

  const endpoint = editingDeviceId ? `/api/devices/${editingDeviceId}` : '/api/devices';
  const method = editingDeviceId ? 'PUT' : 'POST';
  const res = await fetch(endpoint, { method, headers: authHeaders(), body: JSON.stringify(body) });
  const data = await res.json();
  if (!res.ok) return alert(data.error || (editingDeviceId ? 'Failed to update device.' : 'Failed to register device (LAN only policy).'));

  const wasEditing = Boolean(editingDeviceId);
  resetDeviceForm();
  await Promise.all([loadDevices(), loadMonitoring({ force: true, source: 'manual' })]);
  alert(wasEditing ? 'Device updated successfully.' : 'Device registered successfully.');
}

async function loadDevices() {
  const rows = document.getElementById('deviceList');
  if (!rows) return;

  const res = await fetch('/api/devices', { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) {
    rows.innerHTML = `<tr><td colspan='9' class='small text-danger'>${data.error || 'Failed to load devices.'}</td></tr>`;
    return;
  }

  const safe = Array.isArray(data)
    ? data
    : Array.isArray(data?.data)
      ? data.data
      : [];
  deviceRegistry = safe;
  rows.innerHTML = safe.map(d => `<tr>
    <td>${d.deviceName || '-'}</td>
    <td>${d.ipAddress || '-'}</td>
    <td>${d.department || '-'}</td>
    <td>${d.assignedUser || '-'}</td>
    <td><span class='badge ${statusBadge(d.status)}'>${d.status || '-'}</span></td>
    <td>${d.cpuUsage != null ? `${Number(d.cpuUsage).toFixed(0)}%` : '-'}</td>
    <td>${d.memoryUsage != null ? `${Number(d.memoryUsage).toFixed(0)}%` : '-'}</td>
    <td>${formatLastSeen(d.lastSeen)}</td>
    <td>
      <div class='table-actions'>
        <button class='btn btn-ghost icon-only-btn' type='button' onclick='startEditDeviceById(${d.id})' aria-label='Edit ${d.deviceName || 'device'}' title='Edit'>
          ✎
        </button>
        <button class='btn btn-ghost icon-only-btn text-danger' type='button' onclick='deleteDevice(${d.id}, ${JSON.stringify(d.deviceName || '')})' aria-label='Delete ${d.deviceName || 'device'}' title='Delete'>
          🗑
        </button>
      </div>
    </td>
  </tr>`).join('') || `<tr><td colspan='9' class='small'>No devices registered yet.</td></tr>`;
}

function stopMonitoringAutoRefresh() {
  if (!monitoringAutoRefreshTimer) return;
  window.clearInterval(monitoringAutoRefreshTimer);
  monitoringAutoRefreshTimer = null;
}

document.addEventListener('visibilitychange', () => {
  scheduleMonitoringAutoRefresh();
  if (!document.hidden) loadMonitoring({ source: 'visibility' });
});

document.addEventListener('DOMContentLoaded', () => {
  loadMonitoring({ source: 'initial' });
  loadDevices();
  bindAssetAutoFill();
  seedAssignedUserFromSession();
  scheduleMonitoringAutoRefresh();
  window.setInterval(() => updateMonitoringLiveStatus('live'), 1000);
});

window.addEventListener('beforeunload', stopMonitoringAutoRefresh);
