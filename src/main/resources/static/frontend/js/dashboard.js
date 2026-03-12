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
    ['LAN CPU Avg', `${Math.round((cpu.reduce((a, c) => a + Number(c.cpu), 0) / (cpu.length || 1)) * 10) / 10}%`, 'LAN-only infrastructure load'],
    ['Critical Alerts', cpu.filter(c => Number(c.cpu) > 85).length, 'Needs immediate attention']
  ];
  summary.innerHTML = cards.map(([label, value, hint]) => `<div class='card'><div>${label}</div><div class='metric-value'>${value}</div><div class='metric-hint'>${hint}</div></div>`).join('');

  document.getElementById('ticketTrend').textContent = JSON.stringify(trends.points, null, 2);
  document.getElementById('systemHealth').textContent = JSON.stringify(health.points, null, 2);
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
      <td>${u.emailVerified ? 'Yes' : 'No'}</td>
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
