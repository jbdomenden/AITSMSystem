const FOLLOW_UP_HOURS = 24;

function parseDate(v){ const d=new Date(v||''); return Number.isNaN(d.getTime()) ? null : d; }
function hoursSince(v){ const d=parseDate(v); if(!d) return 999; return (Date.now()-d.getTime())/(1000*60*60); }

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
  const res = await fetch(`/api/tickets/${id}/status`, {
    method:'PUT', headers:authHeaders(), body: JSON.stringify({ status })
  });
  const data = await res.json();
  if(!res.ok) return alert(data.error || 'Unable to update ticket status');
  await loadTickets();
}

function actionButtons(ticket){
  const status = ticket.status || '';
  const canFollowUp = !['Resolved','Closed','Cancelled'].includes(status) && hoursSince(ticket.updatedAt) >= FOLLOW_UP_HOURS;
  const parts = [];
  if(status === 'Resolved') parts.push(`<button class='btn btn-primary icon-btn' onclick='updateMyTicketStatus(${ticket.id}, "Closed")' title='Close ticket'>✓</button>`);
  if(['Open','In Progress','Follow-up Requested'].includes(status)) parts.push(`<button class='btn btn-ghost icon-btn' onclick='updateMyTicketStatus(${ticket.id}, "Cancelled")' title='Cancel ticket'>✕</button>`);
  if(canFollowUp) parts.push(`<button class='btn btn-ghost icon-btn' onclick='updateMyTicketStatus(${ticket.id}, "Follow-up Requested")' title='Request follow-up'>↻</button>`);
  return parts.join('');
}

async function loadTickets() {
  const rows = document.getElementById('ticketRows');
  if (!rows) return;
  const role = (localStorage.getItem('role') || '').toLowerCase();
  const res = await fetch('/api/tickets', { headers: authHeaders() });
  const data = await res.json();
  if(!res.ok){ rows.innerHTML = `<tr><td colspan='6' class='small text-danger'>${data.error||'Unable to load tickets'}</td></tr>`; return; }
  const tickets = Array.isArray(data) ? data : [];

  rows.innerHTML = tickets.map(t => {
    const klass = (t.status || 'Open').toLowerCase().replace(/\s+/g, '-');
    const actions = role === 'end-user' ? `<td><div class='inline-actions'>${actionButtons(t)}</div></td>` : '';
    return `<tr>
      <td>#${t.id}</td><td>${t.title}</td><td>${t.priority}</td><td><span class='badge ${klass}'>${t.status}</span></td>
      <td style='color:${t.overdue ? "var(--danger)" : "inherit"}'>${t.slaRemainingMinutes ?? '-'} min</td>
      ${actions}
    </tr>`;
  }).join('') || `<tr><td colspan='6' class='small'>No tickets found.</td></tr>`;
}

document.addEventListener('DOMContentLoaded', loadTickets);
