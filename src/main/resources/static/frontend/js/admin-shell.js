function adminNavItems() {
  return [
    { section: 'Operations', items: [
      { page: 'dashboard-admin.html', href: '/dashboard-admin.html', icon: '▦', label: 'Overview' },
      { page: 'ticket-management.html', href: '/ticket-management.html', icon: '☰', label: 'Ticket Management' },
      { page: 'monitoring.html', href: '/monitoring.html', icon: '◉', label: 'LAN Monitoring' },
      { page: 'assets.html', href: '/assets.html', icon: '◫', label: 'Asset Management' },
      { page: 'knowledge.html', href: '/knowledge.html', icon: '📚', label: 'Knowledge Base' }
    ]},
    { section: 'System', items: [
      { page: 'settings.html', href: '/settings.html', icon: '⚙', label: 'Settings' },
      { page: 'user-management.html', href: '/user-management.html', icon: '👥', label: 'User Management' }
    ]}
  ];
}

function renderAdminSidebar() {
  const host = document.getElementById('adminSidebar');
  if (!host) return;
  const sections = adminNavItems().map(s => `
    <nav class='nav-group'>
      <div class='nav-section-title'>${s.section}</div>
      ${s.items.map(i => `<a class='nav-item' data-page='${i.page}' href='${i.href}'><span class='nav-icon'>${i.icon}</span>${i.label}</a>`).join('')}
    </nav>`).join('');
  host.innerHTML = `<div class='brand'><span class='brand-dot'></span>AITSM Control</div>${sections}`;
}

function closeAdminHeaderMenu(){ document.getElementById('adminHeaderMenu')?.classList.add('hidden'); }
function toggleAdminHeaderMenu(){ document.getElementById('adminHeaderMenu')?.classList.toggle('hidden'); }
function toggleAdminNotifMenu(){ document.getElementById('adminNotifMenu')?.classList.toggle('hidden'); }
function toggleAdminSidebar(){ document.body.classList.toggle('sidebar-hidden'); }

async function loadHeaderNotifications(){
  const list = document.getElementById('adminNotifList');
  const count = document.getElementById('adminNotifCount');
  if(!list || !count) return;
  try {
    const res = await fetch('/api/notifications', { headers: authHeaders() });
    const data = await res.json();
    if(!res.ok) throw new Error(data.error || 'Unable to load notifications');
    const items = (Array.isArray(data)?data:[]).slice(0,8);
    count.textContent = String(items.length);
    list.innerHTML = items.map(n=>`<div class='small' style='padding:8px;border-bottom:1px solid #1f325f'>${n.message || 'Notification'}<br><span style='opacity:.7'>${n.createdAt || ''}</span></div>`).join('') || "<div class='small'>No notifications.</div>";
  } catch {
    list.innerHTML = "<div class='small'>Unable to load notifications.</div>";
  }
}

function renderUtilityHeader() {
  const host = document.getElementById('utilityHeader');
  if (!host) return;
  host.innerHTML = `
    <div class='utility-left'>
      <button class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleAdminSidebar()' aria-label='Toggle sidebar' title='Toggle sidebar'>☰</button>
      <div class='utility-brand'>AITSM Control</div>
    </div>
    <div class='utility-right'>
      <button class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleAdminNotifMenu()' aria-label='Notifications' title='Notifications'>🔔 <span id='adminNotifCount' class='notif-count'>0</span></button>
      <div id='adminNotifMenu' class='admin-header-menu hidden'><div id='adminNotifList' class='small'>Loading...</div></div>
      <button id='adminMenuTrigger' class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleAdminHeaderMenu()' aria-label='Open account menu' title='Account menu'>⋮</button>
      <div id='adminHeaderMenu' class='admin-header-menu hidden'>
        <a href='/knowledge.html'>Help</a>
        <a href='/settings.html'>Change password</a>
        <button type='button' onclick='logout()'>Logout</button>
      </div>
    </div>`;

  document.addEventListener('click', (event) => {
    const trigger = document.getElementById('adminMenuTrigger');
    const menu = document.getElementById('adminHeaderMenu');
    const notifBtn = document.querySelector("button[title='Notifications']");
    const notifMenu = document.getElementById('adminNotifMenu');
    if (menu && trigger && !(menu.contains(event.target) || trigger.contains(event.target))) closeAdminHeaderMenu();
    if (notifMenu && notifBtn && !(notifMenu.contains(event.target) || notifBtn.contains(event.target))) notifMenu.classList.add('hidden');
  });

  loadHeaderNotifications();
}

document.addEventListener('DOMContentLoaded', () => {
  renderAdminSidebar();
  renderUtilityHeader();
});
