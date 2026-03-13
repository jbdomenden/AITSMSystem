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

function renderUtilityHeader() {
  const host = document.getElementById('utilityHeader');
  if (!host) return;
  host.innerHTML = `
    <div class='utility-left'>
      <div class='search-wrap'><span class='search-icon'>⌕</span><input class='search-input' placeholder='Search devices, users, tickets...'></div>
    </div>
    <div class='utility-right'>
      <button class='btn btn-ghost'>Export</button>
      <button class='btn btn-primary'>+ Quick Action</button>
    </div>`;
}

document.addEventListener('DOMContentLoaded', () => {
  renderAdminSidebar();
  renderUtilityHeader();
});
