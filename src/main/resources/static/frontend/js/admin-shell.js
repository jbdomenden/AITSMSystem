function adminIcon(name) {
  const icons = {
    overview: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z'/></svg>",
    tickets: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M5 5h14v4a2 2 0 0 0 0 6v4H5v-4a2 2 0 0 0 0-6V5zm4 3v2h6V8H9zm0 6v2h6v-2H9z'/></svg>",
    monitoring: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M3 17h3l2-5 3 7 3-10 2 8h5v2h-6.5L14 13l-2.5 8L8 14l-1.5 5H3z'/></svg>",
    knowledge: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M5 4.5A2.5 2.5 0 0 1 7.5 2H20v17H7.5A2.5 2.5 0 0 0 5 21.5V4.5zm2.5-.5A1.5 1.5 0 0 0 6 5.5V18a3.5 3.5 0 0 1 1.5-.34H18V4H7.5zM8 7h7v1.5H8zm0 3h7V11.5H8zm0 3h5v1.5H8z'/></svg>",
    settings: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M19.14 12.94a7.96 7.96 0 0 0 .05-.94 7.96 7.96 0 0 0-.05-.94l2.03-1.58-1.92-3.32-2.39.96a7.49 7.49 0 0 0-1.63-.94L14.96 2h-3.92l-.27 2.18c-.58.22-1.12.53-1.63.94l-2.39-.96-1.92 3.32 2.03 1.58a7.96 7.96 0 0 0-.05.94c0 .32.02.63.05.94l-2.03 1.58 1.92 3.32 2.39-.96c.5.41 1.05.72 1.63.94l.27 2.18h3.92l.27-2.18c.58-.22 1.12-.53 1.63-.94l2.39.96 1.92-3.32-2.03-1.58zM13 15.5A3.5 3.5 0 1 1 13 8.5a3.5 3.5 0 0 1 0 7z'/></svg>",
    assets: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M4 6.5 12 3l8 3.5v11L12 21l-8-3.5v-11zm8-.96L6 7.97v8.06l6 2.63 6-2.63V7.97l-6-2.43z'/></svg>",
    users: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M16 11a4 4 0 1 0-3.999-4A4 4 0 0 0 16 11zm-8 1a3 3 0 1 0-3-3 3 3 0 0 0 3 3zm8 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4zM8 14c-.43 0-.9.03-1.39.08C4.57 14.33 1 15.35 1 18v2h6v-2c0-1.2.62-2.23 1.72-3A7.63 7.63 0 0 0 8 14z'/></svg>",
    menu: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M4 7h16v2H4zm0 8h16v2H4zm0-4h16v2H4z'/></svg>",
    bell: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22zm7-5V11a7 7 0 1 0-14 0v6l-2 2v1h18v-1l-2-2z'/></svg>",
    more: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 7a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z' transform='translate(0 -3)'/></svg>",
    help: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 17.2a1.2 1.2 0 1 1 1.2-1.2 1.2 1.2 0 0 1-1.2 1.2zm1.57-7.44-.7.49A2.14 2.14 0 0 0 12 14h-1.5v-.38a3.3 3.3 0 0 1 1.4-2.7l.97-.72a1.61 1.61 0 1 0-2.57-1.29H8.8a3.11 3.11 0 1 1 4.77 2.85z'/></svg>",
    password: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M17 8h-1V6a4 4 0 0 0-8 0v2H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2zm-6 0V6a2 2 0 1 1 4 0v2zm1 8.73V18h-2v-1.27a2 2 0 1 1 2 0z'/></svg>",
    logout: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M10 17v-3H3v-4h7V7l5 5-5 5zm3-12h6v14h-6v2h8V3h-8v2z'/></svg>"
  };
  return `<span class='nav-icon svg-icon'>${icons[name] || ''}</span>`;
}

function adminNavItems() {
  return [
    { section: 'Operations', items: [
      { page: 'dashboard-admin.html', href: '/frontend/html/dashboard-admin.html', icon: 'overview', label: 'Overview' },
      { page: 'ticket-management.html', href: '/frontend/html/ticket-management.html', icon: 'tickets', label: 'Ticket Management' },
      { page: 'monitoring.html', href: '/frontend/html/monitoring.html', icon: 'monitoring', label: 'LAN Monitoring' },
      { page: 'knowledge.html', href: '/frontend/html/knowledge.html', icon: 'knowledge', label: 'Knowledge Base' }
    ]},
    { section: 'System', items: [
      { page: 'settings.html', href: '/frontend/html/settings.html', icon: 'settings', label: 'Settings' },
      { page: 'assets.html', href: '/frontend/html/assets.html', icon: 'assets', label: 'Asset Management' },
      { page: 'user-management.html', href: '/frontend/html/user-management.html', icon: 'users', label: 'User Management' }
    ]}
  ];
}

function renderAdminSidebar() {
  const host = document.getElementById('adminSidebar');
  if (!host) return;
  const sections = adminNavItems().map(s => `
    <nav class='nav-group'>
      <div class='nav-section-title'>${s.section}</div>
      ${s.items.map(i => `<a class='nav-item' data-page='${i.page}' href='${i.href}'>${adminIcon(i.icon)}<span class='nav-label'>${i.label}</span></a>`).join('')}
    </nav>`).join('');
  host.innerHTML = `${sections}`;
}

function closeAdminHeaderMenu(){ document.getElementById('adminHeaderMenu')?.classList.add('hidden'); }
function toggleAdminHeaderMenu(){ document.getElementById('adminHeaderMenu')?.classList.toggle('hidden'); }
function toggleAdminNotifMenu(){ document.getElementById('adminNotifMenu')?.classList.toggle('hidden'); }
function toggleAdminSidebar(){ document.body.classList.toggle('sidebar-hidden'); }

function openAdminHelp() {
  alert('How to use AITSM Admin\n1) Monitor LAN/asset telemetry.\n2) Manage ticket statuses in Ticket Management.\n3) Manage users in User Management.\n4) Use Settings for profile and SLA review.');
}

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
      <button class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleAdminSidebar()' aria-label='Toggle sidebar' title='Toggle sidebar'>${adminIcon('menu')}</button>
      <a class='utility-brand' href='/frontend/html/dashboard-admin.html' aria-label='Go to dashboard overview'>AITSM Control</a>
    </div>
    <div class='utility-right'>
      <button id='adminNotifTrigger' class='btn btn-ghost icon-btn header-icon-btn notif-trigger-btn' type='button' onclick='toggleAdminNotifMenu()' aria-label='Notifications' title='Notifications'>${adminIcon('bell')}<span id='adminNotifCount' class='notif-count'>0</span></button>
      <div id='adminNotifMenu' class='admin-header-menu hidden'><div id='adminNotifList' class='small'>Loading...</div></div>
      <button id='adminMenuTrigger' class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleAdminHeaderMenu()' aria-label='Open account menu' title='Account menu'>${adminIcon('more')}</button>
      <div id='adminHeaderMenu' class='admin-header-menu hidden'>
        <button type='button' class='menu-action-btn' onclick='openAdminHelp()'>${adminIcon('help')}<span>Help</span></button>
        <button type='button' class='menu-action-btn' onclick='openPasswordModal()'>${adminIcon('password')}<span>Change password</span></button>
        <button type='button' class='menu-action-btn danger' onclick='logout()'>${adminIcon('logout')}<span>Logout</span></button>
      </div>
    </div>`;

  document.addEventListener('click', (event) => {
    const trigger = document.getElementById('adminMenuTrigger');
    const menu = document.getElementById('adminHeaderMenu');
    const notifBtn = document.getElementById('adminNotifTrigger');
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
