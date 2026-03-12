function statusBadge(status) {
  const s = String(status || '').toLowerCase();
  if (s === 'critical') return 'open';
  if (s === 'offline') return 'warning';
  return 'resolved';
}

function renderMonitorSummary(devices) {
  const summary = document.getElementById('monitorSummary');
  if (!summary) return;

  const total = devices.length;
  const online = devices.filter(d => String(d.status).toLowerCase() === 'online').length;
  const offline = devices.filter(d => String(d.status).toLowerCase() === 'offline').length;
  const avgCpu = Math.round(devices.reduce((acc, d) => acc + Number(d.cpuUsage || 0), 0) / (total || 1));

  const chips = [
    ['Total Devices', total],
    ['Online', online],
    ['Offline', offline],
    ['Network Health', `${Math.max(0, 100 - avgCpu)}%`]
  ];

  summary.innerHTML = chips.map(([label, value]) => `<div class='kpi-chip'><div class='kpi-label'>${label}</div><div class='kpi-value'>${value}</div></div>`).join('');
}

function renderMonitoringHealthPanel(devices) {
  const panel = document.getElementById('monitorHealthPanel');
  if (!panel) return;

  if (!devices.length) {
    panel.innerHTML = `
      <div class='empty-state'>
        <h3>No LAN telemetry yet</h3>
        <p>Once LAN clients start reporting metrics, this panel will show scan freshness, health trends, and risk indicators.</p>
        <button class='btn btn-primary' onclick='loadMonitoring()'>Retry Scan</button>
      </div>`;
    return;
  }

  const lastSeen = devices.map(d => d.lastSeen).filter(Boolean).sort().reverse()[0] || 'N/A';
  const critical = devices.filter(d => String(d.status).toLowerCase() === 'critical').length;
  const avgMem = Math.round(devices.reduce((acc, d) => acc + Number(d.memoryUsage || 0), 0) / devices.length);

  panel.innerHTML = `
    <div class='insight-grid'>
      <div class='insight-item'><div class='insight-label'>Last scan</div><div class='insight-value'>${lastSeen}</div></div>
      <div class='insight-item'><div class='insight-label'>Critical devices</div><div class='insight-value'>${critical}</div></div>
      <div class='insight-item'><div class='insight-label'>Average memory</div><div class='insight-value'>${avgMem}%</div></div>
      <div class='insight-item'><div class='insight-label'>Monitoring mode</div><div class='insight-value'>LAN Only</div></div>
    </div>`;
}

function renderMonitorCards(devices) {
  const wrap = document.getElementById('monitorCards');
  if (!wrap) return;

  if (!devices.length) {
    wrap.innerHTML = `<div class='empty-state'><h3>No LAN devices found</h3><p>Register assets or send LAN client metrics to populate real-time monitoring.</p><a href='/assets.html' class='btn btn-primary'>Open Asset Management</a></div>`;
    return;
  }

  wrap.innerHTML = devices.map(d => `
    <article class='card'>
      <div class='card-head'><h3 class='section-title'>${d.deviceName}</h3><span class='badge ${statusBadge(d.status)}'>${d.status}</span></div>
      <div class='small'>${d.ipAddress} • ${d.department} • ${d.assignedUser}</div>
      <div class='insight-grid'>
        <div class='insight-item'><div class='insight-label'>CPU</div><div class='insight-value'>${d.cpuUsage}%</div></div>
        <div class='insight-item'><div class='insight-label'>Memory</div><div class='insight-value'>${d.memoryUsage}%</div></div>
      </div>
    </article>`).join('');
}

function renderMonitorTable(devices) {
  const rows = document.getElementById('monitorTableRows');
  if (!rows) return;

  if (!devices.length) {
    rows.innerHTML = `<tr><td colspan='7' class='small'>No LAN devices available. Add assets or wait for client metrics ingestion.</td></tr>`;
    return;
  }

  rows.innerHTML = devices.map(d => `<tr>
    <td>${d.deviceName}</td>
    <td>${d.ipAddress}</td>
    <td>${d.department}</td>
    <td>${d.assignedUser}</td>
    <td>${d.cpuUsage}%</td>
    <td>${d.memoryUsage}%</td>
    <td><span class='badge ${statusBadge(d.status)}'>${d.status}</span></td>
  </tr>`).join('');
}

async function loadMonitoring() {
  const devices = await fetch('/api/monitoring/devices', { headers: authHeaders() }).then(r => r.json());
  const safe = Array.isArray(devices) ? devices : [];

  renderMonitorSummary(safe);
  renderMonitoringHealthPanel(safe);
  renderMonitorCards(safe);
  renderMonitorTable(safe);
}

async function registerDevice() {
  const body = {
    deviceName: deviceName.value.trim(),
    ipAddress: ipAddress.value.trim(),
    department: department.value.trim(),
    assignedUser: assignedUser.value.trim(),
    status: status.value
  };
  const res = await fetch('/api/devices', { method: 'POST', headers: authHeaders(), body: JSON.stringify(body) });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Failed to register device (LAN only policy).');
  deviceName.value = ipAddress.value = department.value = assignedUser.value = '';
  await loadDevices();
  await loadMonitoring();
}

async function loadDevices() {
  const rows = document.getElementById('deviceList');
  if (!rows) return;
  const devices = await fetch('/api/devices', { headers: authHeaders() }).then(r => r.json());
  const safe = Array.isArray(devices) ? devices : [];

  rows.innerHTML = safe.map(d => `<tr><td>${d.deviceName}</td><td>${d.ipAddress}</td><td>${d.assignedUser}</td></tr>`).join('')
    || `<tr><td colspan='3' class='small'>No devices registered yet.</td></tr>`;
}

document.addEventListener('DOMContentLoaded', () => { loadMonitoring(); loadDevices(); });
