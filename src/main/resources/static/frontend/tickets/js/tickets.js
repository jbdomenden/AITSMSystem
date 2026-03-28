
function ensureActionMenuBackdrop() {
  if (document.getElementById('actionMenuBackdrop')) return;
  const backdrop = document.createElement('div');
  backdrop.id = 'actionMenuBackdrop';
  backdrop.className = 'action-menu-backdrop hidden';
  backdrop.addEventListener('click', () => {
    document.querySelectorAll('.row-action-menu').forEach((el) => el.classList.add('hidden'));
  document.getElementById('actionMenuBackdrop')?.classList.add('hidden');
    backdrop.classList.add('hidden');
  });
  document.body.appendChild(backdrop);
}

const FOLLOW_UP_HOURS = 24;

function parseDate(v){ const d=new Date(v||''); return Number.isNaN(d.getTime()) ? null : d; }
function hoursSince(v){ const d=parseDate(v); if(!d) return 999; return (Date.now()-d.getTime())/(1000*60*60); }
function normalizeListResponse(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
}

function closeAllRowMenus() {
  document.querySelectorAll('.row-action-menu').forEach((el) => el.classList.add('hidden'));
  document.getElementById('actionMenuBackdrop')?.classList.add('hidden');
}

function userTicketHref(ticketId) {
  return `/tickets.html?ticketId=${encodeURIComponent(ticketId)}`;
}

function getRequestedTicketId() {
  const params = new URLSearchParams(window.location.search);
  return params.get('ticketId');
}

function focusRequestedTicket() {
  const requestedId = getRequestedTicketId();
  if (!requestedId) return;
  const row = document.querySelector(`[data-ticket-id="${CSS.escape(requestedId)}"]`);
  if (!row) return;
  row.classList.add('ticket-row-focus');
  row.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function toggleRowActionMenu(event, id) {
  event.stopPropagation();
  const menu = document.getElementById(`ticketRowMenu-${id}`);
  if (!menu) return;
  const trigger = event.currentTarget;
  const isHidden = menu.classList.contains('hidden');
  closeAllRowMenus();
  if (!isHidden) return;
  ensureActionMenuBackdrop();
  document.getElementById('actionMenuBackdrop')?.classList.remove('hidden');
  const rect = trigger.getBoundingClientRect();
  menu.style.position = 'fixed';
  menu.style.top = `${rect.bottom + 4}px`;
  menu.style.left = `${Math.max(8, rect.right - 180)}px`;
  menu.classList.remove('hidden');
}

function actionMenu(ticket){
  const rawStatus = String(ticket.status || '').trim();
  const status = rawStatus.toLowerCase();
  const isResolved = status === 'resolved';
  const isOpen = status === 'open';
  const isPending = status === 'pending' || status === 'in progress' || status === 'follow-up requested';
  const canFollowUp = !['resolved', 'closed', 'cancelled'].includes(status) && hoursSince(ticket.updatedAt) >= FOLLOW_UP_HOURS;
  const items = [];
  if (isResolved) items.push(`<button type='button' onclick='updateMyTicketStatus(${ticket.id}, "Closed")'>Close ticket</button>`);
  if (isOpen || isPending) items.push(`<button type='button' onclick='updateMyTicketStatus(${ticket.id}, "Cancelled")'>Cancel ticket</button>`);
  if(canFollowUp) items.push(`<button type='button' onclick='updateMyTicketStatus(${ticket.id}, "Follow-up Requested")'>Request follow-up</button>`);
  if (!items.length) return '-';
  return `<div class='row-action-wrap'>
    <button class='btn btn-ghost icon-btn' onclick='toggleRowActionMenu(event, ${ticket.id})' title='Actions' aria-label='Actions'>⋯</button>
    <div id='ticketRowMenu-${ticket.id}' class='row-action-menu hidden'>${items.join('')}</div>
  </div>`;
}

async function createTicket() {
  const body = {
    title: title.value.trim(),
    description: description.value.trim(),
    priority: priority.value,
    category: category.value
  };
  if (body.description.length < 10) return alert('Description must be at least 10 characters.');
  const res = await fetch('/api/tickets', { method: 'POST', headers: authHeaders(), body: JSON.stringify(body) });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to create ticket');
  alert(`Ticket #${data.id} created successfully.`);
  location.href = '/tickets.html';
}

async function updateMyTicketStatus(id, status){
  closeAllRowMenus();
  const res = await fetch(`/api/tickets/${id}/status`, {
    method:'PUT', headers:authHeaders(), body: JSON.stringify({ status })
  });
  const data = await res.json();
  if(!res.ok) return alert(data.error || 'Unable to update ticket status');
  await loadTickets();
}

async function loadTickets() {
  const rows = document.getElementById('ticketRows');
  if (!rows) return;
  const role = (localStorage.getItem('role') || '').toLowerCase();
  showTableSkeleton(rows, { rowCount: 6, columnCount: 6, hasActions: true });
  try {
    const res = await fetch('/api/tickets', { headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) {
      renderTableErrorState(rows, 6, data.error || 'Unable to load tickets');
      return;
    }
    const tickets = normalizeListResponse(data);

    clearTableSkeleton(rows);
    if (!tickets.length) {
      renderTableEmptyState(rows, 6, 'No tickets found.');
      return;
    }

    rows.innerHTML = tickets.map(t => {
      const klass = (t.status || 'Open').toLowerCase().replace(/\s+/g, '-');
      const actions = role === 'end-user' ? `<td>${actionMenu(t)}</td>` : '<td>-</td>';
      return `<tr data-ticket-id='${t.id}'>
        <td><a class='ticket-link' href='${userTicketHref(t.id)}'>#${t.id}</a></td><td><a class='ticket-link ticket-link-title' href='${userTicketHref(t.id)}' title='${t.title || '-'}'>${t.title || '-'}</a></td><td>${t.priority}</td><td><span class='badge ${klass}'>${t.status}</span></td>
        <td style='color:${t.overdue ? "var(--danger)" : "inherit"}'>${t.slaRemainingMinutes ?? '-'} min</td>
        ${actions}
      </tr>`;
    }).join('');
    focusRequestedTicket();
  } catch {
    renderTableErrorState(rows, 6, 'Unable to load tickets');
  } finally {
    clearTableSkeleton(rows);
  }
}

document.addEventListener('click', () => closeAllRowMenus());
document.addEventListener('DOMContentLoaded', loadTickets);
