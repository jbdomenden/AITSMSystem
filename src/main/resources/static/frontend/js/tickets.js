const headers = window.headers || (() => ({ 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '', 'X-User-Role': localStorage.getItem('role') || '' }));
async function createTicket(){
  const body = { title:title.value, description:description.value, priority:priority.value, category:category.value };
  const res = await fetch('/api/tickets', { method:'POST', headers:headers(), body:JSON.stringify(body) });
  const data = await res.json();
  if(!res.ok) return alert(data.error || 'Unable to create ticket');
  alert('Ticket created'); location.href='/tickets.html';
}

async function loadTickets(){
  const el = document.getElementById('ticketRows'); if(!el) return;
  const tickets = await fetch('/api/tickets', { headers:headers() }).then(r=>r.json());
  el.innerHTML = tickets.map(t=>`<tr><td>${t.id}</td><td>${t.title}</td><td><span class='badge ${t.status.toLowerCase().replace(' ','-')}'>${t.status}</span></td><td style='color:${t.overdue?'var(--danger)':'inherit'}'>${t.slaRemainingMinutes ?? '-'} min</td></tr>`).join('');
}

document.addEventListener('DOMContentLoaded', loadTickets);
