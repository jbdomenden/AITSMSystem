function fmt(v){ const d=new Date(v||''); return Number.isNaN(d.getTime()) ? (v||'-') : d.toLocaleString(); }
function badge(status){ const s=(status||'').toLowerCase(); if(s==='resolved'||s==='closed') return 'resolved'; if(s==='cancelled') return 'warning'; if(s==='in progress'||s==='follow-up requested') return 'in-progress'; return 'open'; }

async function updateTicketStatusAdmin(id, status){
  const res = await fetch(`/api/tickets/${id}/status`, { method:'PUT', headers: authHeaders(), body: JSON.stringify({ status }) });
  const data = await res.json();
  if(!res.ok) return alert(data.error || 'Unable to update status');
  await loadAdminTicketManagement();
}

async function loadAdminTicketManagement(){
  const rows=document.getElementById('adminTicketRows');
  if(!rows) return;
  const res = await fetch('/api/tickets', { headers: authHeaders() });
  const data = await res.json();
  if(!res.ok){ rows.innerHTML=`<tr><td colspan='7' class='small text-danger'>${data.error||'Unable to load tickets'}</td></tr>`; return; }
  const tickets = Array.isArray(data)?data:[];
  rows.innerHTML = tickets.map(t => `<tr>
    <td>#${t.id}</td><td>${t.userId}</td><td>${t.title||'-'}</td><td>${t.priority||'-'}</td>
    <td><span class='badge ${badge(t.status)}'>${t.status||'-'}</span></td>
    <td>${fmt(t.updatedAt)}</td>
    <td><div class='inline-actions'>
      <button class='btn btn-ghost icon-btn' onclick='updateTicketStatusAdmin(${t.id}, "In Progress")' title='Set In Progress'>⏳</button>
      <button class='btn btn-primary icon-btn' onclick='updateTicketStatusAdmin(${t.id}, "Resolved")' title='Set Resolved'>✅</button>
    </div></td>
  </tr>`).join('') || `<tr><td colspan='7' class='small'>No tickets found.</td></tr>`;
}

document.addEventListener('DOMContentLoaded', loadAdminTicketManagement);
