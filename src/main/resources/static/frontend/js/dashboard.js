const headers = window.headers || (() => ({ 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '', 'X-User-Role': localStorage.getItem('role') || '' }));
async function loadAdminDashboard() {
  if (!document.getElementById('summary')) return;
  const [tickets, cpu, trends, health] = await Promise.all([
    fetch('/api/tickets', { headers: headers() }).then(r => r.json()),
    fetch('/api/monitoring/cpu').then(r => r.json()),
    fetch('/api/analytics/ticket-trends').then(r => r.json()),
    fetch('/api/analytics/system-health').then(r => r.json())
  ]);
  const open = tickets.filter(t => t.status === 'Open').length;
  const inProgress = tickets.filter(t => t.status === 'In Progress').length;
  const resolved = tickets.filter(t => t.status === 'Resolved').length;
  const cpuAvg = cpu.length ? Math.round(cpu.reduce((a,c)=>a+Number(c.cpu),0)/cpu.length) : 0;
  summary.innerHTML = [
    ['Total Tickets', tickets.length], ['Open Tickets', open], ['In Progress', inProgress], ['Resolved', resolved],
    ['CPU Usage', `${cpuAvg}%`], ['Memory Usage', 'Live via device cards'], ['Critical Alerts', cpu.filter(c=>Number(c.cpu)>85).length]
  ].map(([k,v])=>`<div class='card'><small>${k}</small><h2>${v}</h2></div>`).join('');
  trend.textContent = JSON.stringify(trends.points, null, 2);
  health.textContent = JSON.stringify(health.points, null, 2);
}

async function loadKnowledge() {
  const el = document.getElementById('knowledgeCards'); if (!el) return;
  const q = (document.getElementById('search')?.value || '').toLowerCase();
  const articles = await fetch('/api/knowledge').then(r=>r.json());
  el.innerHTML = articles.filter(a=>a.title.toLowerCase().includes(q)||a.content.toLowerCase().includes(q)).map(a=>`<div class='card'><h3>${a.title}</h3><small>${a.category}</small><p>${a.content}</p></div>`).join('');
}

document.addEventListener('DOMContentLoaded', ()=>{ loadAdminDashboard(); loadKnowledge(); });
