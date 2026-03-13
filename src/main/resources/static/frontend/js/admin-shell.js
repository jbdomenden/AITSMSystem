function adminNavItems() {
  return [
    { section: 'Operations', items: [
      { page: 'dashboard-admin.html', href: '/dashboard-admin.html', icon: '▦', label: 'Overview' },
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
    </nav>
  `).join('');

  host.innerHTML = `<div class='brand'><span class='brand-dot'></span>AITSM Control</div>${sections}`;
}

function closeAdminHeaderMenu() {
  const menu = document.getElementById('adminHeaderMenu');
  menu?.classList.add('hidden');
}

function toggleAdminHeaderMenu() {
  const menu = document.getElementById('adminHeaderMenu');
  if (!menu) return;
  menu.classList.toggle('hidden');
}

function toggleAdminSidebar() {
  document.body.classList.toggle('sidebar-hidden');
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
    if (!menu || !trigger) return;
    if (menu.contains(event.target) || trigger.contains(event.target)) return;
    closeAdminHeaderMenu();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  renderAdminSidebar();
  renderUtilityHeader();
});
