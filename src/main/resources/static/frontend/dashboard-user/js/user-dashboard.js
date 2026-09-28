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

function collectionFromResponse(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
}

async function fetchJsonOrThrow(url) {
  const res = await fetch(url, { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || `Request failed: ${url}`);
  return data;
}

async function loadUserDashboard() {
  const rows = document.getElementById('recentTicketRows');
  if (rows) showTableSkeleton(rows, { rowCount: 5, columnCount: 5 });

  try {
    const tickets = await fetchJsonOrThrow('/api/tickets');

    const sortedTickets = collectionFromResponse(tickets).sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));

    renderUserSummary(sortedTickets);
    if (rows) clearTableSkeleton(rows);
    renderRecentTickets(sortedTickets);
  } catch (error) {
    renderUserSummary([]);
    if (rows) renderTableErrorState(rows, 5, 'Unable to load tickets right now. Please try again.');
  } finally {
    if (rows) clearTableSkeleton(rows);
  }
}

document.addEventListener('DOMContentLoaded', loadUserDashboard);
