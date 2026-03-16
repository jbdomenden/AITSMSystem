const LAN_AUTO_REFRESH_MS = 5 * 60 * 1000;
let monitoringAutoRefreshTimer = null;

function statusBadge(status) {
  const s = String(status || '').toLowerCase();
  if (s === 'critical') return 'open';
  if (s === 'offline' || s === 'unavailable') return 'warning';
  return 'resolved';
}


function formatLastSeen(value) {
  const date = new Date(value || '');
  return Number.isNaN(date.getTime()) ? (value || '-') : date.toLocaleString();
}

function telemetryBadge(device) {
  if (device.telemetryAvailable) return `<span class='badge resolved'>${device.telemetrySourceType}</span>`;
  return `<span class='badge warning'>UNAVAILABLE</span>`;
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
    <div class='insight-grid'>
      <div class='insight-item'><div class='insight-label'>Host</div><div class='insight-value'>${host.hostname}</div></div>
      <div class='insight-item'><div class='insight-label'>Host Memory</div><div class='insight-value'>${Number(host.memoryUsagePercent ?? 0).toFixed(1)}%</div></div>
      <div class='insight-item'><div class='insight-label'>Telemetry coverage</div><div class='insight-value'>${available}/${devices.length}</div></div>
      <div class='insight-item'><div class='insight-label'>Last updated</div><div class='insight-value'>${summary.timestamp || host.timestamp || 'N/A'}</div></div>
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
    <article class='card'>
      <div class='card-head'><h3 class='section-title'>${d.hostname || d.ipAddress}</h3>${telemetryBadge(d)}</div>
      <div class='small'>${d.ipAddress} • Reachable: ${d.reachable ? 'Yes' : 'No'}</div>
      <div class='insight-grid'>
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

async function refreshDiscovery() {
  const res = await fetch('/api/monitoring/refresh-discovery', { method: 'POST', headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) {
    alert(data.error || 'Failed to refresh discovery');
    return;
  }
  await loadMonitoring();
}

async function loadMonitoring() {
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
}

async function registerDevice() {
  const deviceNameEl = document.getElementById('deviceName');
  const ipAddressEl = document.getElementById('ipAddress');
  const departmentEl = document.getElementById('department');
  const assignedUserEl = document.getElementById('assignedUser');
  const statusEl = document.getElementById('status');

  const body = {
    deviceName: (deviceNameEl?.value || '').trim(),
    ipAddress: (ipAddressEl?.value || '').trim(),
    department: (departmentEl?.value || '').trim(),
    assignedUser: (assignedUserEl?.value || '').trim(),
    status: statusEl?.value || 'Online'
  };

  if (!body.deviceName || !body.ipAddress || !body.department || !body.assignedUser || !body.status) {
    return alert('All device fields are required.');
  }

  const res = await fetch('/api/devices', { method: 'POST', headers: authHeaders(), body: JSON.stringify(body) });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Failed to register device (LAN only policy).');

  if (deviceNameEl) deviceNameEl.value = '';
  if (ipAddressEl) ipAddressEl.value = '';
  if (departmentEl) departmentEl.value = '';
  if (assignedUserEl) assignedUserEl.value = '';
  if (statusEl) statusEl.value = 'Online';

  await loadDevices();
  await loadMonitoring();
}

async function loadDevices() {
  const rows = document.getElementById('deviceList');
  if (!rows) return;

  const res = await fetch('/api/devices', { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) {
    rows.innerHTML = `<tr><td colspan='8' class='small text-danger'>${data.error || 'Failed to load devices.'}</td></tr>`;
    return;
  }

  const safe = Array.isArray(data) ? data : [];
  rows.innerHTML = safe.map(d => `<tr>
    <td>${d.deviceName || '-'}</td>
    <td>${d.ipAddress || '-'}</td>
    <td>${d.department || '-'}</td>
    <td>${d.assignedUser || '-'}</td>
    <td><span class='badge ${statusBadge(d.status)}'>${d.status || '-'}</span></td>
    <td>${d.cpuUsage != null ? `${Number(d.cpuUsage).toFixed(0)}%` : '-'}</td>
    <td>${d.memoryUsage != null ? `${Number(d.memoryUsage).toFixed(0)}%` : '-'}</td>
    <td>${formatLastSeen(d.lastSeen)}</td>
  </tr>`).join('') || `<tr><td colspan='8' class='small'>No devices registered yet.</td></tr>`;
}

function startMonitoringAutoRefresh() {
  if (monitoringAutoRefreshTimer) return;
  monitoringAutoRefreshTimer = window.setInterval(() => {
    loadMonitoring();
    loadDevices();
  }, LAN_AUTO_REFRESH_MS);
}

function stopMonitoringAutoRefresh() {
  if (!monitoringAutoRefreshTimer) return;
  window.clearInterval(monitoringAutoRefreshTimer);
  monitoringAutoRefreshTimer = null;
}

document.addEventListener('DOMContentLoaded', () => {
  loadMonitoring();
  loadDevices();
  startMonitoringAutoRefresh();
});

window.addEventListener('beforeunload', stopMonitoringAutoRefresh);
