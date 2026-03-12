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
    return;
  }

  const cards = [
    ['Total Tickets', tickets.length, 'All incidents'],
    ['Open', tickets.filter(t => t.status === 'Open').length, 'Awaiting action'],
    ['In Progress', tickets.filter(t => t.status === 'In Progress').length, 'Currently handled'],
    ['Resolved', tickets.filter(t => t.status === 'Resolved').length, 'Closed successfully'],
    ['CPU Usage', `${Math.round((cpu.reduce((a, c) => a + Number(c.cpu), 0) / (cpu.length || 1)) * 10) / 10}%`, 'Infrastructure load'],
    ['Critical Alerts', cpu.filter(c => Number(c.cpu) > 85).length, 'Needs immediate attention']
  ];
  summary.innerHTML = cards.map(([label, value, hint]) => `<div class='card'><div>${label}</div><div class='metric-value'>${value}</div><div class='metric-hint'>${hint}</div></div>`).join('');

  document.getElementById('ticketTrend').textContent = JSON.stringify(trends.points, null, 2);
  document.getElementById('systemHealth').textContent = JSON.stringify(health.points, null, 2);
}

async function loadKnowledge() {
  const list = document.getElementById('knowledgeCards');
  if (!list) return;
  const query = (document.getElementById('search')?.value || '').toLowerCase();
  const data = await fetch('/api/knowledge').then(r => r.json());
  const filtered = data.filter(a => a.title.toLowerCase().includes(query) || a.content.toLowerCase().includes(query));
  list.innerHTML = filtered.map(a => `<article class='card'><h3>${a.title}</h3><div class='small'>${a.category}</div><p>${a.content}</p></article>`).join('') || `<div class='card'><p class='small'>No articles found.</p></div>`;
}

document.addEventListener('DOMContentLoaded', () => {
  loadAdminDashboard();
  loadKnowledge();
});
