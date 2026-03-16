function userNavItems() {
  return [
    { section: 'Workspace', items: [
      { page: 'dashboard-user.html', href: '/dashboard-user.html', icon: '▦', label: 'Overview' },
      { page: 'create-ticket.html', href: '/create-ticket.html', icon: '✚', label: 'Create Ticket' },
      { page: 'tickets.html', href: '/tickets.html', icon: '☰', label: 'My Tickets' }
    ] }
  ];
}

function renderUserSidebar() {
  const host = document.getElementById('userSidebar');
  if (!host) return;

  const sections = userNavItems().map((section) => `
    <nav class='nav-group'>
      <div class='nav-section-title'>${section.section}</div>
      ${section.items.map((item) => `<a class='nav-item' data-page='${item.page}' href='${item.href}'><span class='nav-icon'>${item.icon}</span>${item.label}</a>`).join('')}
    </nav>
  `).join('');

  host.innerHTML = `<div class='brand'><span class='brand-dot'></span>AITSM Portal</div>${sections}`;
}

function openUserHelp() {
  alert('How to use AITSM:\n1) Create a ticket with clear issue details.\n2) Track status in My Tickets.\n3) Close resolved tickets, cancel active tickets, or request follow-up after 24h unresolved.');
}

function closeUserHeaderMenu() {
  const menu = document.getElementById('userHeaderMenu');
  menu?.classList.add('hidden');
}

function toggleUserHeaderMenu() {
  const menu = document.getElementById('userHeaderMenu');
  if (!menu) return;
  menu.classList.toggle('hidden');
}

function toggleUserSidebar() {
  document.body.classList.toggle('sidebar-hidden');
}

function renderUserUtilityHeader() {
  const host = document.getElementById('utilityHeader');
  if (!host) return;

  host.innerHTML = `
    <div class='utility-left'>
      <button class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleUserSidebar()' aria-label='Toggle sidebar' title='Toggle sidebar'>☰</button>
      <div class='utility-brand'>AITSM Portal</div>
    </div>
    <div class='utility-right'>
      <button id='userMenuTrigger' class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleUserHeaderMenu()' aria-label='Open account menu' title='Account menu'>⋮</button>
      <div id='userHeaderMenu' class='admin-header-menu hidden'>
        <button type='button' onclick='openUserHelp()'>Help</button>
        <button type='button' onclick='openPasswordModal()'>Change password</button>
        <button type='button' onclick='logout()'>Logout</button>
      </div>
    </div>`;

  document.addEventListener('click', (event) => {
    const trigger = document.getElementById('userMenuTrigger');
    const menu = document.getElementById('userHeaderMenu');
    if (!menu || !trigger) return;
    if (menu.contains(event.target) || trigger.contains(event.target)) return;
    closeUserHeaderMenu();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  renderUserSidebar();
  renderUserUtilityHeader();
});
