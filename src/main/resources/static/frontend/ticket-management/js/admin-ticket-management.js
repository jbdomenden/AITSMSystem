
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

function fmt(v){ const d=new Date(v||''); return Number.isNaN(d.getTime()) ? (v||'-') : d.toLocaleString(); }
function badge(status){ const s=(status||'').toLowerCase(); if(s==='resolved'||s==='closed') return 'resolved'; if(s==='cancelled') return 'warning'; if(s==='in progress'||s==='follow-up requested') return 'in-progress'; return 'open'; }
function normalizeListResponse(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
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

async function updateTicketStatusAdmin(id, status){
  closeAdminRowMenus();
  const res = await fetch(`/api/tickets/${id}/status`, { method:'PUT', headers: authHeaders(), body: JSON.stringify({ status }) });
  const data = await res.json();
  if(!res.ok) return alert(data.error || 'Unable to update status');
  await loadAdminTicketManagement();
}

function actionMenu(ticket){
  return `<div class='row-action-wrap'>
    <button class='btn btn-ghost icon-btn' onclick='toggleAdminRowMenu(event, ${ticket.id})' title='Actions' aria-label='Actions'>⋯</button>
    <div id='adminTicketRowMenu-${ticket.id}' class='row-action-menu hidden'>
      <button type='button' onclick='updateTicketStatusAdmin(${ticket.id}, "In Progress")'>Set In Progress</button>
      <button type='button' onclick='updateTicketStatusAdmin(${ticket.id}, "Resolved")'>Set Resolved</button>
      <button type='button' onclick='updateTicketStatusAdmin(${ticket.id}, "Cancelled")'>Set Cancelled</button>
    </div>
  </div>`;
}

async function loadAdminTicketManagement(){
  const rows=document.getElementById('adminTicketRows');
  if(!rows) return;
  const res = await fetch('/api/tickets', { headers: authHeaders() });
  const data = await res.json();
  if(!res.ok){ rows.innerHTML=`<tr><td colspan='7' class='small text-danger'>${data.error||'Unable to load tickets'}</td></tr>`; return; }
  const tickets = normalizeListResponse(data);
  rows.innerHTML = tickets.map(t => `<tr data-ticket-id='${t.id}'>
    <td><a class='ticket-link' href='${adminTicketHref(t.id)}'>#${t.id}</a></td><td>${t.userId}</td><td><a class='ticket-link ticket-link-title' href='${adminTicketHref(t.id)}' title='${t.title || '-'}'>${t.title||'-'}</a></td><td>${t.priority||'-'}</td>
    <td><span class='badge ${badge(t.status)}'>${t.status||'-'}</span></td>
    <td>${fmt(t.updatedAt)}</td>
    <td>${actionMenu(t)}</td>
  </tr>`).join('') || `<tr><td colspan='7' class='small'>No tickets found.</td></tr>`;
  focusRequestedAdminTicket();
}

document.addEventListener('click', () => closeAdminRowMenus());
document.addEventListener('DOMContentLoaded', loadAdminTicketManagement);
