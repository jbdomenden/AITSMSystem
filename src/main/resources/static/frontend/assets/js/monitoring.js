const LAN_AUTO_REFRESH_VISIBLE_MS = 10 * 1000;
const LAN_AUTO_REFRESH_HIDDEN_MS = 30 * 1000;
let monitoringAutoRefreshTimer = null;
let monitoringRefreshInFlight = false;
let monitoringLastUpdatedAt = null;
let editingDeviceId = null;
let deviceRegistry = [];
let assetUserDirectory = [];
let assetDepartmentDirectory = new Set();

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

function setAssetFormMessage(message = '', tone = 'info') {
  const el = document.getElementById('assetFormMessage');
  if (!el) return;
  el.textContent = message;
  el.classList.remove('text-success', 'text-danger');
  if (tone === 'success') el.classList.add('text-success');
  if (tone === 'danger') el.classList.add('text-danger');
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
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
    renderTableEmptyState(rows, 7, 'No LAN devices discovered yet.');
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

function renderLanIpSuggestions(ips) {
  const suggestions = document.getElementById('lanIpSuggestions');
  if (!suggestions) return;

  const uniqueIps = [...new Set((ips || []).map((ip) => (ip || '').trim()).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }));

  suggestions.innerHTML = uniqueIps.map((ip) => `<option value="${ip}"></option>`).join('');
}

function updateDepartmentSelectOptions(selectedValue = '') {
  const select = document.getElementById('department');
  if (!select) return;
  const departments = [...assetDepartmentDirectory].filter(Boolean).sort((a, b) => a.localeCompare(b));
  select.innerHTML = [`<option value=''>Select department</option>`]
    .concat(departments.map((department) => `<option value='${escapeHtml(department)}'>${escapeHtml(department)}</option>`))
    .join('');
  if (selectedValue && departments.includes(selectedValue)) select.value = selectedValue;
}

function updateAssignedUserSelectOptions(selectedValue = '') {
  const select = document.getElementById('assignedUser');
  if (!select) return;
  const options = assetUserDirectory.map((user) => {
    const value = user.fullName || user.email;
    const label = user.email ? `${user.fullName || user.email} (${user.email})` : (user.fullName || 'User');
    return { value, label };
  }).filter((entry) => entry.value);

  select.innerHTML = [`<option value=''>Select user</option>`]
    .concat(options.map((entry) => `<option value='${escapeHtml(entry.value)}'>${escapeHtml(entry.label)}</option>`))
    .join('');
  if (selectedValue && options.some((entry) => entry.value === selectedValue)) select.value = selectedValue;
}

function includeDynamicFormOptionsFromDevices(devices) {
  (Array.isArray(devices) ? devices : []).forEach((device) => {
    const department = String(device.department || '').trim();
    if (department) assetDepartmentDirectory.add(department);
  });
  updateDepartmentSelectOptions(document.getElementById('department')?.value || '');
}

async function loadAssetFormOptions() {
  const defaultDepartments = ['Finance', 'HR', 'IT', 'Operations', 'Sales', 'Support'];
  defaultDepartments.forEach((department) => assetDepartmentDirectory.add(department));

  try {
    const res = await fetch('/api/users', { headers: authHeaders() });
    const users = await res.json();
    if (res.ok && Array.isArray(users)) {
      assetUserDirectory = users.map((user) => ({
        fullName: String(user.fullName || '').trim(),
        email: String(user.email || '').trim(),
        department: String(user.department || '').trim()
      }));
      assetUserDirectory.forEach((user) => {
        if (user.department) assetDepartmentDirectory.add(user.department);
      });
    }
  } catch {
    assetUserDirectory = [];
  }

  updateDepartmentSelectOptions();
  updateAssignedUserSelectOptions();
}

function isValidLanIpAddress(ipAddress) {
  const pattern = /^(\\d{1,3}\\.){3}\\d{1,3}$/;
  if (!pattern.test(ipAddress)) return false;
  return ipAddress.split('.').every((part) => Number(part) >= 0 && Number(part) <= 255);
}

function validateDeviceForm(body) {
  if (!body.deviceName) return 'Device name is required. Use a discoverable LAN IP so the hostname can be detected.';
  if (!body.ipAddress) return 'IP address is required.';
  if (!isValidLanIpAddress(body.ipAddress)) return 'Enter a valid IPv4 address for the LAN device.';
  if (!body.department) return 'Select a department.';
  if (!body.assignedUser) return 'Select an assigned user.';
  if (!body.status) return 'Device status is required.';
  return null;
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
  const monitorRows = document.getElementById('monitorTableRows');
  if (monitorRows) showTableSkeleton(monitorRows, { rowCount: 6, columnCount: 7 });

  try {
    const [summaryRes, devicesRes, peerIpsRes] = await Promise.all([
      fetch('/api/monitoring/summary', { headers: authHeaders() }),
      fetch('/api/monitoring/lan-devices', { headers: authHeaders() }),
      fetch('/api/monitoring/lan-peer-ips', { headers: authHeaders() })
    ]);

    const summary = await summaryRes.json();
    const devices = await devicesRes.json();
    const peerIps = await peerIpsRes.json();
    const safeDevices = Array.isArray(devices) ? devices : [];
    const safePeerIps = Array.isArray(peerIps) ? peerIps : [];

    renderMonitorSummary(summaryRes.ok ? summary : null);
    renderMonitoringHealthPanel(summaryRes.ok ? summary : null, safeDevices);
    renderMonitorCards(safeDevices);
    if (monitorRows) clearTableSkeleton(monitorRows);
    renderMonitorTable(safeDevices);
    renderLanIpSuggestions(safePeerIps);

    monitoringLastUpdatedAt = new Date();
    updateMonitoringLiveStatus('live', source === 'discovery' ? 'Discovery refreshed just now' : undefined);
  } catch {
    if (monitorRows) renderTableErrorState(monitorRows, 7, 'Unable to load monitoring table data.');
    updateMonitoringLiveStatus('error');
  } finally {
    if (monitorRows) clearTableSkeleton(monitorRows);
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
    if (!assignedInput.value.trim() && data.assignedUser) {
      const assigned = String(data.assignedUser || '').trim();
      if (assigned) {
        const exists = assetUserDirectory.some((entry) => entry.fullName === assigned || entry.email === assigned);
        if (!exists) {
          assetUserDirectory.push({ fullName: assigned, email: '', department: '' });
          updateAssignedUserSelectOptions(assigned);
        }
        assignedInput.value = assigned;
      }
    }
    setStatusField(data.suggestedStatus);
  } catch {
    setStatusField('Unreachable');
  }
}

function seedAssignedUserFromSession() {
  const assignedInput = document.getElementById('assignedUser');
  if (!assignedInput) return;
  if ((assignedInput.value || '').trim()) return;
  const fullName = (localStorage.getItem('fullName') || '').trim();
  const email = (localStorage.getItem('email') || '').trim();
  const preferred = fullName || email;
  if (!preferred) return;

  const alreadyPresent = assetUserDirectory.some((entry) => entry.fullName === preferred || entry.email === preferred);
  if (!alreadyPresent) {
    assetUserDirectory.push({ fullName: preferred, email, department: '' });
    updateAssignedUserSelectOptions(preferred);
  }

  assignedInput.value = preferred;
}

async function refreshAssetConnections() {
  setAssetFormMessage('');
  const res = await fetch('/api/devices/sync-from-monitoring', { method: 'POST', headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) {
    setAssetFormMessage(data.error || 'Unable to refresh device connections', 'danger');
    return;
  }
  await Promise.all([loadDevices(), loadMonitoring({ force: true, source: 'manual' })]);
  setAssetFormMessage(data.message || 'Asset connections refreshed', 'success');
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
  setAssetFormMessage('');
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
  setAssetFormMessage('');

  if (device.department) {
    assetDepartmentDirectory.add(device.department);
    updateDepartmentSelectOptions(device.department);
  }
  if (device.assignedUser) {
    const existing = assetUserDirectory.some((entry) => entry.fullName === device.assignedUser || entry.email === device.assignedUser);
    if (!existing) assetUserDirectory.push({ fullName: device.assignedUser, email: '', department: device.department || '' });
    updateAssignedUserSelectOptions(device.assignedUser);
  }

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
  if (!res.ok) {
    setAssetFormMessage(data.error || 'Failed to delete device.', 'danger');
    return;
  }

  if (editingDeviceId === id) resetDeviceForm();
  await Promise.all([loadDevices(), loadMonitoring({ force: true, source: 'manual' })]);
  setAssetFormMessage(data.message || 'Device deleted', 'success');
}

async function registerDevice() {
  const deviceNameEl = document.getElementById('deviceName');
  const ipAddressEl = document.getElementById('ipAddress');
  setAssetFormMessage('');

  if (!(deviceNameEl?.value || '').trim() && (ipAddressEl?.value || '').trim()) {
    await autoFillDeviceContextByIp();
  }

  const body = currentDeviceFormBody();
  const validationError = validateDeviceForm(body);
  if (validationError) {
    setAssetFormMessage(validationError, 'danger');
    return;
  }

  const endpoint = editingDeviceId ? `/api/devices/${editingDeviceId}` : '/api/devices';
  const method = editingDeviceId ? 'PUT' : 'POST';
  const res = await fetch(endpoint, { method, headers: authHeaders(), body: JSON.stringify(body) });
  const data = await res.json();
  if (!res.ok) {
    setAssetFormMessage(data.error || (editingDeviceId ? 'Failed to update device.' : 'Failed to register device (LAN only policy).'), 'danger');
    return;
  }

  const wasEditing = Boolean(editingDeviceId);
  resetDeviceForm();
  await Promise.all([loadDevices(), loadMonitoring({ force: true, source: 'manual' })]);
  setAssetFormMessage(wasEditing ? 'Device updated successfully.' : 'Device registered successfully.', 'success');
}

async function loadDevices() {
  const rows = document.getElementById('deviceList');
  if (!rows) return;
  showTableSkeleton(rows, { rowCount: 6, columnCount: 9, hasActions: true });

  const res = await fetch('/api/devices', { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) {
    renderTableErrorState(rows, 9, data.error || 'Failed to load devices.');
    return;
  }

  const safe = Array.isArray(data)
    ? data
    : Array.isArray(data?.data)
      ? data.data
      : [];
  deviceRegistry = safe;
  includeDynamicFormOptionsFromDevices(safe);
  safe.forEach((device) => {
    const label = String(device.assignedUser || '').trim();
    if (!label) return;
    const exists = assetUserDirectory.some((entry) => entry.fullName === label || entry.email === label);
    if (!exists) {
      assetUserDirectory.push({
        fullName: label,
        email: '',
        department: String(device.department || '').trim()
      });
    }
  });
  updateAssignedUserSelectOptions(document.getElementById('assignedUser')?.value || '');
  clearTableSkeleton(rows);
  if (!safe.length) {
    renderTableEmptyState(rows, 9, 'No devices registered yet.');
    return;
  }
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
  </tr>`).join('');
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

document.addEventListener('DOMContentLoaded', async () => {
  await loadAssetFormOptions();
  seedAssignedUserFromSession();
  loadMonitoring({ source: 'initial' });
  loadDevices();
  bindAssetAutoFill();
  scheduleMonitoringAutoRefresh();
  window.setInterval(() => updateMonitoringLiveStatus('live'), 1000);
});

window.addEventListener('beforeunload', stopMonitoringAutoRefresh);
