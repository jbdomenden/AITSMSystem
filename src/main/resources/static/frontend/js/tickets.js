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

async function loadTickets() {
  const rows = document.getElementById('ticketRows');
  if (!rows) return;
  const tickets = await fetch('/api/tickets', { headers: authHeaders() }).then(r => r.json());
  rows.innerHTML = tickets.map(t => {
    const klass = t.status.toLowerCase().replace(' ', '-');
    return `<tr>
      <td>#${t.id}</td><td>${t.title}</td><td>${t.priority}</td><td><span class='badge ${klass}'>${t.status}</span></td>
      <td style='color:${t.overdue ? "var(--danger)" : "inherit"}'>${t.slaRemainingMinutes ?? '-'} min</td>
    </tr>`;
  }).join('');
}

document.addEventListener('DOMContentLoaded', loadTickets);
