function toChartSeries(points, fallback = []) {
  if (!Array.isArray(points) || points.length === 0) return fallback;
  return points.map((p, i) => {
    const raw = Number(Object.values(p || {}).find(v => !Number.isNaN(Number(v))));
    return Number.isFinite(raw) ? raw : (fallback[i] ?? 0);
  });
}

function renderSparkBars(elId, values, labels = []) {
  const el = document.getElementById(elId);
  if (!el) return;
  const safeValues = (values?.length ? values : [0, 0, 0, 0]).map(v => Number(v) || 0);
  const max = Math.max(1, ...safeValues);

  el.innerHTML = `<div class='spark-chart'>${safeValues.map((v, i) => {
    const h = Math.max(6, Math.round((v / max) * 120));
    return `<div class='spark-col'>
      <div class='spark-bar-wrap'><div class='spark-bar' style='height:${h}px'></div></div>
      <div class='spark-value'>${v}</div>
      <div class='spark-label'>${labels[i] || `P${i + 1}`}</div>
    </div>`;
  }).join('')}</div>`;
}

function renderHealthInsights(items) {
  const el = document.getElementById('systemHealthInsights');
  if (!el) return;
  const safe = Array.isArray(items) ? items : [];
  if (!safe.length) {
    el.innerHTML = `<div class='insight-item'><div class='insight-label'>Status</div><div class='insight-value'>No data</div></div>`;
    return;
  }
  el.innerHTML = safe.map(i => `<div class='insight-item'><div class='insight-label'>${Object.values(i)[0] || 'Insight'}</div><div class='insight-value'>${Object.values(i)[1] || '-'}</div></div>`).join('');
}

async function loadAdminDashboard() {
  const summary = document.getElementById('summaryCards');
  if (!summary) return;

  const [tickets, cpu, trends, health] = await Promise.all([
    fetch('/api/tickets', { headers: authHeaders() }).then(r => r.json()),
    fetch('/api/monitoring/cpu', { headers: authHeaders() }).then(r => r.json()),
    fetch('/api/analytics/ticket-trends', { headers: authHeaders() }).then(r => r.json()),
    fetch('/api/analytics/system-health', { headers: authHeaders() }).then(r => r.json())
  ]);

  if (tickets.error || cpu.error || trends.error || health.error) {
    summary.innerHTML = `<div class='card'><p class='small'>Unable to load admin analytics. Please verify you are logged in as admin.</p></div>`;
    renderSparkBars('ticketTrend', [0, 0, 0, 0], ['W1', 'W2', 'W3', 'W4']);
    renderSparkBars('systemHealth', [0, 0, 0], ['Health', 'Spikes', 'Stability']);
    renderHealthInsights([]);
    return;
  }

  const cards = [
    ['◧', 'Total Tickets', tickets.length, 'All incidents'],
    ['◍', 'Open', tickets.filter(t => t.status === 'Open').length, 'Awaiting action'],
    ['◔', 'In Progress', tickets.filter(t => t.status === 'In Progress').length, 'Currently handled'],
    ['✓', 'Resolved', tickets.filter(t => t.status === 'Resolved').length, 'Closed successfully'],
    ['⚙', 'LAN CPU Avg', `${Math.round((cpu.reduce((a, c) => a + Number(c.cpu), 0) / (cpu.length || 1)) * 10) / 10}%`, 'LAN-only infrastructure load'],
    ['⚠', 'Critical Alerts', cpu.filter(c => Number(c.cpu) > 85).length, 'Needs immediate attention']
  ];

  summary.innerHTML = cards.map(([icon, label, value, hint]) => `
    <article class='card metric-card'>
      <div class='card-head'><div class='metric-label'>${label}</div><span class='card-icon'>${icon}</span></div>
      <div class='metric-value'>${value}</div>
      <div class='metric-hint'>${hint}</div>
    </article>`).join('');

  const trendVals = toChartSeries(trends.points, [0, 0, 0, 0]);
  const healthVals = toChartSeries(health.points, [0, 0, 0]);
  renderSparkBars('ticketTrend', trendVals, ['W1', 'W2', 'W3', 'W4']);
  renderSparkBars('systemHealth', healthVals, ['Health', 'Spikes', 'Stability']);
  renderHealthInsights(health.points);
}

async function loadUsers() {
  const rows = document.getElementById('userRows');
  if (!rows) return;

  const users = await fetch('/api/users', { headers: authHeaders() }).then(r => r.json());
  if (users.error) {
    rows.innerHTML = `<tr><td colspan='5'>${users.error}</td></tr>`;
    return;
  }

  rows.innerHTML = users.map(u => {
    const canChange = u.role !== 'superadmin';
    const action = u.role === 'admin'
      ? `<button class='btn btn-ghost' ${canChange ? '' : 'disabled'} onclick='changeRole(${u.id}, "end-user")'>Set End-User</button>`
      : `<button class='btn btn-primary' ${canChange ? '' : 'disabled'} onclick='changeRole(${u.id}, "admin")'>Make Admin</button>`;

    return `<tr>
      <td>${u.fullName}</td>
      <td>${u.email}</td>
      <td><span class='badge ${u.role === 'admin' || u.role === 'superadmin' ? 'in-progress' : 'resolved'}'>${u.role}</span></td>
      <td>${u.emailVerified ? '<span class="badge resolved">Verified</span>' : '<span class="badge warning">Pending</span>'}</td>
      <td>${action}</td>
    </tr>`;
  }).join('');
}

async function changeRole(userId, role) {
  const res = await fetch(`/api/users/${userId}/role`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ role })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to update role');
  await loadUsers();
}

document.addEventListener('DOMContentLoaded', () => {
  loadAdminDashboard();
  loadUsers();
});
