const adminTicketState = {
  tickets: [],
  usersById: new Map(),
  filters: {
    search: '',
    status: 'all',
    priority: 'all',
    sort: 'updated-desc'
  }
};

function ensureActionMenuBackdrop() {
  if (document.getElementById('actionMenuBackdrop')) return;
  const backdrop = document.createElement('div');
  backdrop.id = 'actionMenuBackdrop';
  backdrop.className = 'action-menu-backdrop hidden';
  backdrop.addEventListener('click', () => {
    closeAdminRowMenus();
    backdrop.classList.add('hidden');
  });
  document.body.appendChild(backdrop);
}

function normalizeTicketList(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
}

function parseDate(value) {
  const date = new Date(value || '');
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value) {
  const parsed = parseDate(value);
  return parsed ? parsed.toLocaleString() : (value || '-');
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function normalizeStatus(value) {
  const status = String(value || '').trim().replaceAll('_', ' ').toLowerCase();
  if (status === 'pending' || status === 'in progress' || status === 'follow-up requested' || status === 'follow up requested') {
    return 'in progress';
  }
  if (status === 'resolved') return 'resolved';
  if (status === 'closed' || status === 'cancelled' || status === 'canceled') return 'closed';
  return 'open';
}

function statusLabel(value) {
  const normalized = normalizeStatus(value);
  if (normalized === 'in progress') return 'In Progress';
  if (normalized === 'resolved') return 'Resolved';
  if (normalized === 'closed') return 'Closed';
  return 'Open';
}

function badge(status) {
  const normalized = normalizeStatus(status);
  if (normalized === 'resolved' || normalized === 'closed') return 'resolved';
  if (normalized === 'in progress') return 'in-progress';
  return 'open';
}

function closeAdminRowMenus() {
  document.querySelectorAll('.row-action-menu').forEach((el) => el.classList.add('hidden'));
  document.getElementById('actionMenuBackdrop')?.classList.add('hidden');
}

function adminTicketHref(ticketId) {
  return `/ticket-management.html?ticketId=${encodeURIComponent(ticketId)}`;
}

function getRequestedAdminTicketId() {
  const params = new URLSearchParams(window.location.search);
  return params.get('ticketId');
}

function focusRequestedAdminTicket() {
  const requestedId = getRequestedAdminTicketId();
  if (!requestedId) return;

  const row = document.querySelector(`[data-ticket-id="${CSS.escape(requestedId)}"]`);
  if (!row) return;

  row.classList.add('ticket-row-focus');
  row.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function toggleAdminRowMenu(event, id) {
  event.stopPropagation();
  const menu = document.getElementById(`adminTicketRowMenu-${id}`);
  if (!menu) return;
  const trigger = event.currentTarget;
  const open = menu.classList.contains('hidden');
  closeAdminRowMenus();
  if (!open) return;
  ensureActionMenuBackdrop();
  document.getElementById('actionMenuBackdrop')?.classList.remove('hidden');
  const rect = trigger.getBoundingClientRect();
  menu.style.position = 'fixed';
  menu.style.top = `${rect.bottom + 4}px`;
  menu.style.left = `${Math.max(8, rect.right - 180)}px`;
  menu.classList.remove('hidden');
}

async function updateTicketStatusAdmin(id, status) {
  closeAdminRowMenus();
  const res = await fetch(`/api/tickets/${id}/status`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ status })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to update status');
  await loadAdminTicketManagement();
}

function actionMenu(ticket) {
  return `<div class='row-action-wrap'>
    <button class='btn btn-ghost icon-btn' onclick='toggleAdminRowMenu(event, ${ticket.id})' title='Actions' aria-label='Actions'>...</button>
    <div id='adminTicketRowMenu-${ticket.id}' class='row-action-menu hidden'>
      <button type='button' onclick='updateTicketStatusAdmin(${ticket.id}, "Open")'>Set Open</button>
      <button type='button' onclick='updateTicketStatusAdmin(${ticket.id}, "In Progress")'>Set In Progress</button>
      <button type='button' onclick='updateTicketStatusAdmin(${ticket.id}, "Resolved")'>Set Resolved</button>
      <button type='button' onclick='updateTicketStatusAdmin(${ticket.id}, "Closed")'>Set Closed</button>
    </div>
  </div>`;
}

function requesterLabel(userId) {
  const user = adminTicketState.usersById.get(Number(userId));
  if (!user) return `User #${userId}`;
  const fullName = user.fullName || `User #${userId}`;
  return user.email ? `${fullName} (${user.email})` : fullName;
}

function priorityRank(priority) {
  const order = { Critical: 4, High: 3, Medium: 2, Low: 1 };
  return order[String(priority || '').trim()] || 0;
}

function applyTicketFilters() {
  const query = adminTicketState.filters.search.trim().toLowerCase();
  const statusFilter = adminTicketState.filters.status;
  const priorityFilter = adminTicketState.filters.priority;

  const filtered = adminTicketState.tickets.filter((ticket) => {
    const normalizedStatus = normalizeStatus(ticket.status);
    const matchesStatus = statusFilter === 'all' || normalizedStatus === statusFilter;
    const matchesPriority = priorityFilter === 'all' || String(ticket.priority || '').trim() === priorityFilter;

    if (!query) return matchesStatus && matchesPriority;

    const tokens = [
      ticket.id,
      ticket.title,
      ticket.category,
      ticket.priority,
      ticket.status,
      requesterLabel(ticket.userId)
    ].map((value) => String(value || '').toLowerCase());

    return matchesStatus && matchesPriority && tokens.some((token) => token.includes(query));
  });

  const sorted = [...filtered];
  const sortValue = adminTicketState.filters.sort;

  sorted.sort((a, b) => {
    if (sortValue === 'priority-desc') return priorityRank(b.priority) - priorityRank(a.priority);
    if (sortValue === 'priority-asc') return priorityRank(a.priority) - priorityRank(b.priority);
    if (sortValue === 'id-asc') return Number(a.id || 0) - Number(b.id || 0);
    if (sortValue === 'id-desc') return Number(b.id || 0) - Number(a.id || 0);

    const aTime = parseDate(a.updatedAt || a.createdAt)?.getTime() || 0;
    const bTime = parseDate(b.updatedAt || b.createdAt)?.getTime() || 0;
    if (sortValue === 'updated-asc') return aTime - bTime;
    return bTime - aTime;
  });

  return sorted;
}

function renderAdminTicketCount(count, total) {
  const el = document.getElementById('adminTicketCount');
  if (!el) return;
  el.textContent = `${count} shown / ${total} total`;
}

function renderAdminTicketRows() {
  const rows = document.getElementById('adminTicketRows');
  if (!rows) return;

  const visible = applyTicketFilters();
  renderAdminTicketCount(visible.length, adminTicketState.tickets.length);

  if (!visible.length) {
    renderTableEmptyState(rows, 8, 'No tickets matched the selected filters.');
    return;
  }

  rows.innerHTML = visible.map((ticket) => `
    <tr data-ticket-id='${ticket.id}'>
      <td><a class='ticket-link' href='${adminTicketHref(ticket.id)}'>#${ticket.id}</a></td>
      <td class='monitor-text-wrap' title='${escapeHtml(requesterLabel(ticket.userId))}'>${escapeHtml(requesterLabel(ticket.userId))}</td>
      <td><a class='ticket-link ticket-link-title' href='${adminTicketHref(ticket.id)}' title='${escapeHtml(ticket.title || '-')}'>${escapeHtml(ticket.title || '-')}</a></td>
      <td>${escapeHtml(ticket.category || '-')}</td>
      <td>${escapeHtml(ticket.priority || '-')}</td>
      <td><span class='badge ${badge(ticket.status)}'>${statusLabel(ticket.status)}</span></td>
      <td>${formatDate(ticket.updatedAt || ticket.createdAt)}</td>
      <td>${actionMenu(ticket)}</td>
    </tr>
  `).join('');

  focusRequestedAdminTicket();
}

async function loadAdminTicketManagement() {
  const rows = document.getElementById('adminTicketRows');
  if (!rows) return;

  showTableSkeleton(rows, { rowCount: 8, columnCount: 8, hasActions: true });

  try {
    const [ticketsRes, usersRes] = await Promise.all([
      fetch('/api/tickets?limit=500', { headers: authHeaders() }),
      fetch('/api/users', { headers: authHeaders() })
    ]);

    const ticketsData = await ticketsRes.json();
    const usersData = await usersRes.json();

    if (!ticketsRes.ok) {
      renderTableErrorState(rows, 8, ticketsData.error || 'Unable to load tickets');
      return;
    }

    if (!usersRes.ok) {
      renderTableErrorState(rows, 8, usersData.error || 'Unable to load requester data');
      return;
    }

    adminTicketState.tickets = normalizeTicketList(ticketsData);
    adminTicketState.usersById = new Map((Array.isArray(usersData) ? usersData : []).map((user) => [Number(user.id), user]));

    clearTableSkeleton(rows);
    renderAdminTicketRows();
  } catch {
    renderTableErrorState(rows, 8, 'Unable to load tickets');
  } finally {
    clearTableSkeleton(rows);
  }
}

function wireAdminTicketFilters() {
  const bind = (id, key, eventName = 'change') => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener(eventName, () => {
      adminTicketState.filters[key] = el.value || 'all';
      renderAdminTicketRows();
    });
  };

  bind('adminTicketStatusFilter', 'status');
  bind('adminTicketPriorityFilter', 'priority');
  bind('adminTicketSort', 'sort');

  const search = document.getElementById('adminTicketSearch');
  if (search) {
    search.addEventListener('input', () => {
      adminTicketState.filters.search = search.value || '';
      renderAdminTicketRows();
    });
  }
}

document.addEventListener('click', () => closeAdminRowMenus());
document.addEventListener('DOMContentLoaded', () => {
  wireAdminTicketFilters();
  loadAdminTicketManagement();
});
