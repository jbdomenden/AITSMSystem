function badgeClassForStatus(status) {
  const normalized = (status || '').toLowerCase();
  if (normalized === 'open') return 'open';
  if (normalized === 'in progress') return 'in-progress';
  if (normalized === 'resolved') return 'resolved';
  return 'warning';
}

function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function userTicketHref(ticketId) {
  return `/tickets.html?ticketId=${encodeURIComponent(ticketId)}`;
}

function renderUserSummary(tickets) {
  const el = document.getElementById('userSummaryCards');
  if (!el) return;

  const safeTickets = Array.isArray(tickets) ? tickets : [];
  const open = safeTickets.filter(t => t.status === 'Open').length;
  const inProgress = safeTickets.filter(t => t.status === 'In Progress').length;
  const resolved = safeTickets.filter(t => t.status === 'Resolved').length;

  const cards = [
    ['◧', 'Total Tickets', safeTickets.length, 'All tickets you submitted'],
    ['◍', 'Open', open, 'Awaiting assignment or triage'],
    ['◔', 'In Progress', inProgress, 'Currently worked by support'],
    ['✓', 'Resolved', resolved, 'Completed requests']
  ];

  el.innerHTML = cards.map(([icon, label, value, hint]) => `
    <article class='card metric-card'>
      <div class='card-head'><div class='metric-label'>${label}</div><span class='card-icon'>${icon}</span></div>
      <div class='metric-value'>${value}</div>
      <div class='metric-hint'>${hint}</div>
    </article>`).join('');
}

function renderRecentTickets(tickets) {
  const rows = document.getElementById('recentTicketRows');
  if (!rows) return;

  const safeTickets = Array.isArray(tickets) ? tickets : [];
  if (!safeTickets.length) {
    renderTableEmptyState(rows, 5, 'No tickets yet. Create your first ticket to get started.');
    return;
  }

  rows.innerHTML = safeTickets.slice(0, 5).map(t => `
    <tr>
      <td><a class='ticket-link' href='${userTicketHref(t.id)}'>#${t.id}</a></td>
      <td><a class='ticket-link ticket-link-title' href='${userTicketHref(t.id)}' title='${t.title || '-'}'>${t.title || '-'}</a></td>
      <td>${t.priority || '-'}</td>
      <td><span class='badge ${badgeClassForStatus(t.status)}'>${t.status || 'Unknown'}</span></td>
      <td>${formatDateTime(t.updatedAt)}</td>
    </tr>
  `).join('');
}

function renderNotifications(items) {
  const el = document.getElementById('userNotifications');
  if (!el) return;

  const safe = Array.isArray(items) ? items : [];
  if (!safe.length) {
    el.innerHTML = "<div class='empty-state'><h3>No notifications</h3><p>Updates from support team will appear here.</p></div>";
    return;
  }

  el.innerHTML = safe.slice(0, 6).map(n => `
    <article class='notification-item'>
      <div class='notification-line'>
        <span class='badge ${n.type === 'error' ? 'open' : (n.type === 'success' ? 'resolved' : 'in-progress')}'>${n.type || 'info'}</span>
        <span class='small'>${formatDateTime(n.createdAt)}</span>
      </div>
      <p>${n.message || 'Notification received.'}</p>
    </article>
  `).join('');
}

async function fetchJsonOrThrow(url) {
  const res = await fetch(url, { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || `Request failed: ${url}`);
  return data;
}

async function loadUserDashboard() {
  const rows = document.getElementById('recentTicketRows');
  const notifications = document.getElementById('userNotifications');
  if (rows) showTableSkeleton(rows, { rowCount: 5, columnCount: 5 });
  if (notifications) notifications.innerHTML = "<p class='small'>Loading notifications...</p>";

  try {
    const [tickets, userNotifications] = await Promise.all([
      fetchJsonOrThrow('/api/tickets'),
      fetchJsonOrThrow('/api/notifications')
    ]);

    const sortedTickets = [...tickets].sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));
    const sortedNotifications = [...userNotifications].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

    renderUserSummary(sortedTickets);
    if (rows) clearTableSkeleton(rows);
    renderRecentTickets(sortedTickets);
    renderNotifications(sortedNotifications);
  } catch (error) {
    renderUserSummary([]);
    if (rows) renderTableErrorState(rows, 5, error.message);
    if (notifications) notifications.innerHTML = `<p class='small text-danger'>${error.message}</p>`;
  } finally {
    if (rows) clearTableSkeleton(rows);
  }
}

document.addEventListener('DOMContentLoaded', loadUserDashboard);
