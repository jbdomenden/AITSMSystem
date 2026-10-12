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
  return `/tickets?ticketId=${encodeURIComponent(ticketId)}`;
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
      <td><a class='ticket-link' href='${userTicketHref(t.id)}'>#${Number(t.id)}</a></td>
      <td><a class='ticket-link ticket-link-title' href='${userTicketHref(t.id)}' title='${aitsmEscapeHtml(t.title || '-')}'>${aitsmEscapeHtml(t.title || '-')}</a></td>
      <td>${aitsmEscapeHtml(t.priority || '-')}</td>
      <td><span class='badge ${badgeClassForStatus(t.status)}'>${aitsmEscapeHtml(t.status || 'Unknown')}</span></td>
      <td>${aitsmEscapeHtml(formatDateTime(t.updatedAt))}</td>
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

async function downloadMyCollector() {
  const button = document.getElementById('downloadMyCollectorBtn');
  const status = document.getElementById('myCollectorStatus');
  button.disabled = true; status.textContent = 'Creating your secure installer…'; status.className = 'small';
  try {
    const response = await fetch('/api/monitoring/collector-packages', { method: 'POST', headers: authHeaders(), body: JSON.stringify({ name: `${window.currentUser?.fullName || 'User'} Windows device`, siteId: 'self-device', scope: 'SELF' }) });
    const enrollment = await response.json();
    if (!response.ok) throw new Error(enrollment.error || 'Unable to create enrollment');
    const download = await fetch('/api/monitoring/collector-installer/windows', { method: 'POST', headers: authHeaders(), body: JSON.stringify({ enrollmentCode: enrollment.enrollmentCode }) });
    if (!download.ok) { const data = await download.json().catch(() => ({})); throw new Error(data.error || 'Installer download failed'); }
    const disposition = download.headers.get('content-disposition') || '';
    const filename = disposition.match(/filename="([^"]+)"/i)?.[1] || 'AITSMCollectorSetup.exe';
    const url = URL.createObjectURL(await download.blob());
    const link = document.createElement('a'); link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
    status.textContent = 'Downloaded. Run the installer within 10 minutes and approve the Windows prompt.'; status.className = 'small text-success';
  } catch (error) { status.textContent = error.message || 'Unable to download collector.'; status.className = 'small text-danger'; }
  finally { button.disabled = false; }
}

async function loadMyCollectors() {
  const target = document.getElementById('myCollectors');
  const response = await fetch('/api/monitoring/my-collectors', { headers: authHeaders() });
  const collectors = await response.json();
  if (!response.ok) throw new Error(collectors.error || 'Unable to load collectors');
  target.innerHTML = collectors.length ? collectors.map(item => `<div class='insight-item'><div><strong>${aitsmEscapeHtml(item.name)}</strong><div class='small'>${aitsmEscapeHtml(item.state)} · ${item.lastSeenAt ? aitsmEscapeHtml(formatDateTime(item.lastSeenAt)) : 'Never connected'}</div></div>${item.state === 'REVOKED' ? '' : `<button class='btn btn-ghost' type='button' onclick='revokeMyCollector("${item.collectorId}")'>Remove</button>`}</div>`).join('') : `<p class='small'>No collector installed for your account.</p>`;
}

async function revokeMyCollector(id) {
  if (!confirm('Remove this collector from your account?')) return;
  const response = await fetch(`/api/monitoring/my-collectors/${encodeURIComponent(id)}`, { method: 'DELETE', headers: authHeaders() });
  if (!response.ok) { const data = await response.json().catch(() => ({})); return showAppAlert(data.error || 'Unable to remove collector'); }
  await loadMyCollectors();
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
    await loadMyCollectors();
  } catch (error) {
    renderUserSummary([]);
    if (rows) renderTableErrorState(rows, 5, 'Unable to load tickets right now. Please try again.');
  } finally {
    if (rows) clearTableSkeleton(rows);
  }
}

document.addEventListener('DOMContentLoaded', async () => {
  await window.aitsmReady;
  document.getElementById('downloadMyCollectorBtn')?.addEventListener('click', downloadMyCollector);
  await loadUserDashboard();
});
