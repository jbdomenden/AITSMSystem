function userIcon(name) {
  const icons = {
    overview: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z'/></svg>",
    create: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z'/></svg>",
    tickets: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M5 5h14v4a2 2 0 0 0 0 6v4H5v-4a2 2 0 0 0 0-6V5zm4 3v2h6V8H9zm0 6v2h6v-2H9z'/></svg>",
    menu: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M4 7h16v2H4zm0 8h16v2H4zm0-4h16v2H4z'/></svg>",
    more: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 7a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z' transform='translate(0 -3)'/></svg>",
    help: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 17.2a1.2 1.2 0 1 1 1.2-1.2 1.2 1.2 0 0 1-1.2 1.2zm1.57-7.44-.7.49A2.14 2.14 0 0 0 12 14h-1.5v-.38a3.3 3.3 0 0 1 1.4-2.7l.97-.72a1.61 1.61 0 1 0-2.57-1.29H8.8a3.11 3.11 0 1 1 4.77 2.85z'/></svg>",
    password: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M17 8h-1V6a4 4 0 0 0-8 0v2H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2zm-6 0V6a2 2 0 1 1 4 0v2zm1 8.73V18h-2v-1.27a2 2 0 1 1 2 0z'/></svg>",
    logout: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M10 17v-3H3v-4h7V7l5 5-5 5zm3-12h6v14h-6v2h8V3h-8v2z'/></svg>"
  };
  return `<span class='nav-icon svg-icon'>${icons[name] || ''}</span>`;
}

function userNavItems() {
  return [
    { section: 'Workspace', items: [
      { page: 'dashboard-user.html', href: '/dashboard-user.html', icon: 'overview', label: 'Overview' },
      { page: 'create-ticket.html', href: '/create-ticket.html', icon: 'create', label: 'Create Ticket' },
      { page: 'tickets.html', href: '/tickets.html', icon: 'tickets', label: 'My Tickets' }
    ] }
  ];
}

function renderUserSidebar() {
  const host = document.getElementById('userSidebar');
  if (!host) return;

  const sections = userNavItems().map((section) => `
    <nav class='nav-group'>
      <div class='nav-section-title'>${section.section}</div>
      ${section.items.map((item) => `<a class='nav-item' data-page='${item.page}' href='${item.href}'>${userIcon(item.icon)}<span class='nav-label'>${item.label}</span></a>`).join('')}
    </nav>
  `).join('');

  host.innerHTML = sections;
}

function openUserHelp() {
  alert('How to use AITSM:\n1) Create a ticket with clear issue details.\n2) Track status in My Tickets.\n3) Close resolved tickets, cancel active tickets, or request follow-up after 24h unresolved.');
}

function closeUserHeaderMenu() {
  document.getElementById('userHeaderMenu')?.classList.add('hidden');
}

function toggleUserHeaderMenu() {
  document.getElementById('userHeaderMenu')?.classList.toggle('hidden');
}

function toggleUserSidebar() {
  document.body.classList.toggle('sidebar-hidden');
}

function renderUserUtilityHeader() {
  const host = document.getElementById('utilityHeader');
  if (!host) return;

  host.innerHTML = `
    <div class='utility-left'>
      <button class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleUserSidebar()' aria-label='Toggle sidebar' title='Toggle sidebar'>${userIcon('menu')}</button>
      <a class='utility-brand' href='/dashboard-user.html' aria-label='Go to user dashboard overview'>AITSM Portal</a>
    </div>
    <div class='utility-right'>
      <button id='userMenuTrigger' class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleUserHeaderMenu()' aria-label='Open account menu' title='Account menu'>${userIcon('more')}</button>
      <div id='userHeaderMenu' class='admin-header-menu hidden'>
        <button type='button' class='menu-action-btn' onclick='openUserHelp()'>${userIcon('help')}<span>Help</span></button>
        <button type='button' class='menu-action-btn' onclick='openPasswordModal()'>${userIcon('password')}<span>Change password</span></button>
        <button type='button' class='menu-action-btn danger' onclick='logout()'>${userIcon('logout')}<span>Logout</span></button>
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
