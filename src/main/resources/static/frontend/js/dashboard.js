function parseDate(value) {
  const date = new Date(value || '');
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDateTime(value) {
  const parsed = parseDate(value);
  return parsed ? parsed.toLocaleString() : (value || '—');
}

function sortByUpdatedDesc(items, key = 'updatedAt') {
  return [...(Array.isArray(items) ? items : [])].sort((a, b) => {
    const aTime = parseDate(a?.[key])?.getTime() ?? 0;
    const bTime = parseDate(b?.[key])?.getTime() ?? 0;
    return bTime - aTime;
  });
}

async function fetchJsonOrThrow(url, options = {}) {
  const res = await fetch(url, { headers: authHeaders(), ...options });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || `Failed request: ${url}`);
  return data;
}

function calculateDailyTicketTrend(tickets) {
  const names = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  const open = new Array(7).fill(0);
  const resolved = new Array(7).fill(0);
  const now = new Date();

  (Array.isArray(tickets) ? tickets : []).forEach(ticket => {
    const d = parseDate(ticket.updatedAt || ticket.createdAt);
    if (!d) return;
    const diffDays = Math.floor((now - d) / (1000 * 60 * 60 * 24));
    if (diffDays < 0 || diffDays > 6) return;
    const idx = d.getDay() === 0 ? 6 : d.getDay() - 1;
    if (ticket.status === 'Resolved') resolved[idx] += 1;
    else open[idx] += 1;
  });

  return { labels: names, open, resolved };
}

function renderLineChart(elId, labels, openSeries, resolvedSeries) {
  const el = document.getElementById(elId);
  if (!el) return;
  const width = 600;
  const height = 280;
  const pad = { top: 24, right: 20, bottom: 44, left: 36 };
  const maxValue = Math.max(1, ...openSeries, ...resolvedSeries);
  const plotW = width - pad.left - pad.right;
  const plotH = height - pad.top - pad.bottom;

  const x = i => pad.left + (i * (plotW / Math.max(1, labels.length - 1)));
  const y = v => pad.top + (plotH - ((v / maxValue) * plotH));
  const toPath = series => series.map((v, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(v)}`).join(' ');

  const grid = [0, 0.25, 0.5, 0.75, 1].map(r => {
    const gy = pad.top + (plotH * r);
    return `<line x1='${pad.left}' y1='${gy}' x2='${width - pad.right}' y2='${gy}' stroke='#e2e8f0' stroke-dasharray='3 4'/>`;
  }).join('');

  el.innerHTML = `
    <svg viewBox='0 0 ${width} ${height}' class='viz-svg' role='img' aria-label='Ticket trend chart'>
      ${grid}
      <path d='${toPath(openSeries)}' fill='none' stroke='#2563eb' stroke-width='2.5' stroke-linecap='round'/>
      <path d='${toPath(resolvedSeries)}' fill='none' stroke='#22c55e' stroke-width='2.5' stroke-linecap='round'/>
      ${labels.map((label, i) => `<text x='${x(i)}' y='${height - 14}' text-anchor='middle' class='viz-axis-label'>${label}</text>`).join('')}
      <text x='${pad.left}' y='${pad.top - 8}' class='viz-axis-hint'>0-${maxValue}</text>
    </svg>
    <div class='viz-legend'>
      <span><i class='viz-dot open'></i>open</span>
      <span><i class='viz-dot resolved'></i>resolved</span>
    </div>`;
}

function getPriorityCounts(tickets) {
  const counts = { Critical: 0, High: 0, Medium: 0, Low: 0 };
  (Array.isArray(tickets) ? tickets : []).forEach(t => {
    const key = (t.priority || '').trim();
    if (counts[key] != null) counts[key] += 1;
  });
  return counts;
}

function renderPriorityPie(elId, counts) {
  const el = document.getElementById(elId);
  if (!el) return;
  const labels = ['High', 'Critical', 'Low', 'Medium'];
  const colors = { High: '#f97316', Critical: '#ef4444', Low: '#22c55e', Medium: '#eab308' };
  const values = labels.map(l => counts[l] || 0);
  const total = Math.max(1, values.reduce((a, b) => a + b, 0));

  let start = 0;
  const cx = 170;
  const cy = 130;
  const r = 72;

  const arcs = values.map((v, i) => {
    const angle = (v / total) * Math.PI * 2;
    const end = start + angle;
    const x1 = cx + r * Math.cos(start);
    const y1 = cy + r * Math.sin(start);
    const x2 = cx + r * Math.cos(end);
    const y2 = cy + r * Math.sin(end);
    const large = angle > Math.PI ? 1 : 0;
    const path = v === 0 ? '' : `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`;
    start = end;
    return `<path d='${path}' fill='${colors[labels[i]]}' stroke='#fff' stroke-width='1'/>`;
  }).join('');

  const legends = labels.map(label => `<span class='viz-priority ${label.toLowerCase()}'>${label}: ${counts[label] || 0}</span>`).join('');

  el.innerHTML = `
    <div class='pie-wrap'>
      <svg viewBox='0 0 340 260' class='viz-svg' role='img' aria-label='Priority distribution pie chart'>${arcs}</svg>
      <div class='pie-legend'>${legends}</div>
    </div>`;
}

function generatePerformanceSnapshots(summary, devices) {
  const hostCpu = Number(summary?.hostTelemetry?.cpuUsagePercent ?? 0);
  const hostMem = Number(summary?.hostTelemetry?.memoryUsagePercent ?? 0);
  const coverage = Number(summary?.telemetryAvailableDevices ?? 0);
  const total = Math.max(1, Number(summary?.totalDiscovered ?? 1));
  const coveragePct = (coverage / total) * 100;
  const avgDeviceCpu = (devices.length ? devices.reduce((acc, d) => acc + Number(d.cpuUsagePercent ?? 0), 0) / devices.length : 0);

  return [
    ['00:00', hostCpu * 0.72, hostMem * 0.8, coveragePct * 0.85],
    ['04:00', hostCpu * 0.6, hostMem * 0.75, coveragePct * 0.9],
    ['08:00', hostCpu * 0.82, hostMem * 0.86, coveragePct * 0.88],
    ['12:00', Math.max(hostCpu, avgDeviceCpu), hostMem, coveragePct * 0.92],
    ['16:00', hostCpu * 0.92, hostMem * 0.94, coveragePct * 0.93],
    ['20:00', hostCpu * 0.7, hostMem * 0.84, coveragePct * 0.97]
  ].map(([time, cpu, mem, tele]) => ({
    time,
    cpu: Math.min(100, Math.max(0, Math.round(cpu))),
    memory: Math.min(100, Math.max(0, Math.round(mem))),
    telemetry: Math.min(100, Math.max(0, Math.round(tele)))
  }));
}

function renderSystemPerformance(elId, snapshots) {
  const el = document.getElementById(elId);
  if (!el) return;
  const max = Math.max(100, ...snapshots.flatMap(s => [s.cpu, s.memory, s.telemetry]));

  el.innerHTML = `
    <div class='perf-grid'>
      ${snapshots.map(s => `
        <div class='perf-slot'>
          <div class='perf-bars'>
            <span class='perf-bar cpu' style='height:${(s.cpu / max) * 100}%'></span>
            <span class='perf-bar memory' style='height:${(s.memory / max) * 100}%'></span>
            <span class='perf-bar telemetry' style='height:${(s.telemetry / max) * 100}%'></span>
          </div>
          <div class='spark-label'>${s.time}</div>
        </div>
      `).join('')}
    </div>
    <div class='viz-legend'>
      <span><i class='viz-dot cpu'></i>CPU</span>
      <span><i class='viz-dot memory'></i>Memory</span>
      <span><i class='viz-dot telemetry'></i>Telemetry</span>
    </div>`;
}

function renderRecentAdminTickets(tickets) {
  const rows = document.getElementById('recentAdminTicketRows');
  if (!rows) return;

  const priorityOrder = { Critical: 0, High: 1, Medium: 2, Low: 3 };
  const relevant = sortByUpdatedDesc(tickets)
    .filter(t => ['Critical', 'High', 'Medium'].includes(t.priority))
    .sort((a, b) => (priorityOrder[a.priority] ?? 99) - (priorityOrder[b.priority] ?? 99))
    .slice(0, 6);

  if (!relevant.length) {
    rows.innerHTML = "<tr><td colspan='5' class='small'>No recent high-priority tickets found.</td></tr>";
    return;
  }

  rows.innerHTML = relevant.map(t => `
    <tr>
      <td>#${t.id}</td>
      <td>${t.title || '-'}</td>
      <td>${t.priority || '-'}</td>
      <td><span class='badge ${t.status === 'Open' ? 'open' : (t.status === 'In Progress' ? 'in-progress' : 'resolved')}'>${t.status || 'Unknown'}</span></td>
      <td>${formatDateTime(t.updatedAt)}</td>
    </tr>
  `).join('');
}

function renderAdminOpsFeed(alerts, notifications) {
  const feed = document.getElementById('adminOpsFeed');
  if (!feed) return;

  const criticalAlerts = (Array.isArray(alerts) ? alerts : []).map(a => ({
    type: 'critical',
    message: `${a.hostname || a.ipAddress || 'Device'} has high usage (CPU ${Number(a.cpuUsagePercent ?? 0).toFixed(1)}%, Memory ${Number(a.memoryUsagePercent ?? 0).toFixed(1)}%).`,
    createdAt: a.lastSeen || new Date().toISOString()
  }));

  const mappedNotifications = (Array.isArray(notifications) ? notifications : []).map(n => ({
    type: n.type || 'info',
    message: n.message || 'Notification received.',
    createdAt: n.createdAt
  }));

  const all = sortByUpdatedDesc([...criticalAlerts, ...mappedNotifications], 'createdAt').slice(0, 8);
  if (!all.length) {
    feed.innerHTML = "<div class='empty-state'><h3>No operational updates</h3><p>Alerts and notifications will appear here.</p></div>";
    return;
  }

  feed.innerHTML = all.map(item => {
    const badge = item.type === 'critical' || item.type === 'error'
      ? 'open'
      : (item.type === 'success' ? 'resolved' : 'in-progress');
    return `<article class='notification-item'>
      <div class='notification-line'>
        <span class='badge ${badge}'>${item.type}</span>
        <span class='small'>${formatDateTime(item.createdAt)}</span>
      </div>
      <p>${item.message}</p>
    </article>`;
  }).join('');
}

function renderHealthInsights(items) {
  const el = document.getElementById('systemHealthInsights');
  if (!el) return;
  const safe = Array.isArray(items) ? items : [];
  if (!safe.length) {
    el.innerHTML = `<div class='insight-item'><div class='insight-label'>Status</div><div class='insight-value'>No data</div></div>`;
    return;
  }
  el.innerHTML = safe.map(i => `<div class='insight-item'><div class='insight-label'>${i.label || 'Insight'}</div><div class='insight-value'>${i.value || '-'}</div></div>`).join('');
}


function setAnalyticsBuffering(isLoading) {
  const summary = document.getElementById('summaryCards');
  const trend = document.getElementById('ticketTrendChart');
  const priority = document.getElementById('priorityDistributionChart');
  const perf = document.getElementById('systemPerformanceChart');

  if (isLoading) {
    if (summary) {
      summary.innerHTML = Array.from({ length: 4 }).map(() => `
        <article class='card metric-card analytics-skeleton'>
          <div class='metric-label'>Buffering...</div>
          <div class='metric-value'>--</div>
          <div class='metric-hint'>Loading metrics</div>
        </article>`).join('');
    }

    [trend, priority, perf].forEach((el) => {
      if (!el) return;
      el.classList.add('analytics-buffering');
      el.innerHTML = `<div class='analytics-buffer-msg'><span class='page-splash-spinner' aria-hidden='true'></span><span>Buffering analytics...</span></div>`;
    });
    return;
  }

  [trend, priority, perf].forEach((el) => el?.classList.remove('analytics-buffering'));
}

async function loadAdminDashboard() {
  const summary = document.getElementById('summaryCards');
  if (!summary) return;

  setAnalyticsBuffering(true);

  try {
    const [tickets, monitorSummary, lanDevices, notifications] = await Promise.all([
      fetchJsonOrThrow('/api/tickets'),
      fetchJsonOrThrow('/api/monitoring/summary'),
      fetchJsonOrThrow('/api/monitoring/lan-devices'),
      fetchJsonOrThrow('/api/notifications')
    ]);

    const safeTickets = Array.isArray(tickets) ? tickets : [];
    const safeDevices = Array.isArray(lanDevices) ? lanDevices : [];
    const host = monitorSummary?.hostTelemetry || {};

    const openCount = safeTickets.filter(t => t.status === 'Open').length;
    const inProgressCount = safeTickets.filter(t => t.status === 'In Progress').length;
    const resolvedCount = safeTickets.filter(t => t.status === 'Resolved').length;
    const criticalAlerts = safeDevices.filter(d => (Number(d.cpuUsagePercent ?? 0) > 85) || (Number(d.memoryUsagePercent ?? 0) > 90));

    const cards = [
      ['◧', 'Total Tickets', safeTickets.length, 'From live ticket backend'],
      ['◍', 'Open', openCount, 'Awaiting action'],
      ['◔', 'In Progress', inProgressCount, 'Currently handled'],
      ['✓', 'Resolved', resolvedCount, 'Closed successfully'],
      ['⚙', 'Host CPU Usage', `${Number(host.cpuUsagePercent ?? 0).toFixed(1)}%`, `Host ${host.hostname || 'local'} (${host.ipAddress || 'n/a'})`],
      ['🧠', 'Host Memory', `${Number(host.memoryUsagePercent ?? 0).toFixed(1)}%`, `${Number(monitorSummary?.telemetryAvailableDevices ?? 0)} telemetry-enabled devices`],
      ['⚠', 'Critical Alerts', criticalAlerts.length, 'Derived from LAN telemetry']
    ];

    summary.innerHTML = cards.map(([icon, label, value, hint]) => `
      <article class='card metric-card'>
        <div class='card-head'><div class='metric-label'>${label}</div><span class='card-icon'>${icon}</span></div>
        <div class='metric-value'>${value}</div>
        <div class='metric-hint'>${hint}</div>
      </article>`).join('');

    const trend = calculateDailyTicketTrend(safeTickets);
    renderLineChart('ticketTrendChart', trend.labels, trend.open, trend.resolved);

    const priorities = getPriorityCounts(safeTickets);
    renderPriorityPie('priorityDistributionChart', priorities);

    const snapshots = generatePerformanceSnapshots(monitorSummary, safeDevices);
    renderSystemPerformance('systemPerformanceChart', snapshots);

    renderHealthInsights([
      { label: 'Host', value: host.hostname || 'N/A' },
      { label: 'Monitored devices', value: `${monitorSummary?.monitoredDevices ?? 0}` },
      { label: 'Critical devices', value: `${criticalAlerts.length}` },
      { label: 'Last updated', value: formatDateTime(monitorSummary?.timestamp) }
    ]);

    renderRecentAdminTickets(safeTickets);
    renderAdminOpsFeed(criticalAlerts, notifications);
  } catch (error) {
    summary.innerHTML = `<div class='card'><p class='small'>${error.message}</p></div>`;
    renderLineChart('ticketTrendChart', ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'], [0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0]);
    renderPriorityPie('priorityDistributionChart', { Critical: 0, High: 0, Medium: 0, Low: 0 });
    renderSystemPerformance('systemPerformanceChart', [
      { time: '00:00', cpu: 0, memory: 0, telemetry: 0 },
      { time: '04:00', cpu: 0, memory: 0, telemetry: 0 },
      { time: '08:00', cpu: 0, memory: 0, telemetry: 0 },
      { time: '12:00', cpu: 0, memory: 0, telemetry: 0 },
      { time: '16:00', cpu: 0, memory: 0, telemetry: 0 },
      { time: '20:00', cpu: 0, memory: 0, telemetry: 0 }
    ]);
    renderHealthInsights([]);
    renderRecentAdminTickets([]);
    renderAdminOpsFeed([], []);
  } finally {
    setAnalyticsBuffering(false);
  }
}



document.addEventListener('DOMContentLoaded', () => {
  loadAdminDashboard();
});

