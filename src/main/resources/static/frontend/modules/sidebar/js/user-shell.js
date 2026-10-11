function userIcon(name) {
  const icons = {
    overview: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z'/></svg>",
    create: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z'/></svg>",
    tickets: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M5 5h14v4a2 2 0 0 0 0 6v4H5v-4a2 2 0 0 0 0-6V5zm4 3v2h6V8H9zm0 6v2h6v-2H9z'/></svg>",
    knowledge: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M5 4.5A2.5 2.5 0 0 1 7.5 2H20v17H7.5A2.5 2.5 0 0 0 5 21.5V4.5zm2.5-.5A1.5 1.5 0 0 0 6 5.5V18a3.5 3.5 0 0 1 1.5-.34H18V4H7.5zM8 7h7v1.5H8zm0 3h7V11.5H8zm0 3h5v1.5H8z'/></svg>",
    assistant: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 2a5 5 0 0 0-5 5v1H6a3 3 0 0 0-3 3v5a3 3 0 0 0 3 3h1v1a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-1h1a3 3 0 0 0 3-3v-5a3 3 0 0 0-3-3h-1V7a5 5 0 0 0-5-5zm-3 6V7a3 3 0 0 1 6 0v1H9zm1.5 6.2a1.2 1.2 0 1 0 0-2.4 1.2 1.2 0 0 0 0 2.4zm3 0a1.2 1.2 0 1 0 0-2.4 1.2 1.2 0 0 0 0 2.4z'/></svg>",
    menu: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M4 7h16v2H4zm0 8h16v2H4zm0-4h16v2H4z'/></svg>",
    bell: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22zm7-5V11a7 7 0 1 0-14 0v6l-2 2v1h18v-1l-2-2z'/></svg>",
    chat: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M4 3h16a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H9l-5 4v-4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zm1 4v2h14V7H5zm0 4v2h10v-2H5z'/></svg>",
    more: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 7a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z' transform='translate(0 -3)'/></svg>",
    profile: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 12a5 5 0 1 0-5-5 5 5 0 0 0 5 5zm0 2c-4.42 0-8 1.79-8 4v2h16v-2c0-2.21-3.58-4-8-4z'/></svg>",
    help: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 17.2a1.2 1.2 0 1 1 1.2-1.2 1.2 1.2 0 0 1-1.2 1.2zm1.57-7.44-.7.49A2.14 2.14 0 0 0 12 14h-1.5v-.38a3.3 3.3 0 0 1 1.4-2.7l.97-.72a1.61 1.61 0 1 0-2.57-1.29H8.8a3.11 3.11 0 1 1 4.77 2.85z'/></svg>",
    password: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M17 8h-1V6a4 4 0 0 0-8 0v2H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2zm-6 0V6a2 2 0 1 1 4 0v2zm1 8.73V18h-2v-1.27a2 2 0 1 1 2 0z'/></svg>",
    logout: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M10 17v-3H3v-4h7V7l5 5-5 5zm3-12h6v14h-6v2h8V3h-8v2z'/></svg>"
  };
  return `<span class='nav-icon svg-icon'>${icons[name] || ''}</span>`;
}

function userShellEscape(value) {
  return String(value ?? '').replace(/[&<>'"]/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[char]));
}

function userNavItems() {
  return [
    { section: 'Workspace', items: [
      { page: 'dashboard-user', href: '/dashboard-user', icon: 'overview', label: 'Overview' },
      { page: 'create-ticket', href: '/create-ticket', icon: 'create', label: 'Create Ticket' },
      { page: 'tickets', href: '/tickets', icon: 'tickets', label: 'My Tickets' },
      { page: 'knowledge-library', href: '/knowledge-library', icon: 'knowledge', label: 'Knowledge Base' },
      { page: 'ai-assistant', href: '/ai-assistant', icon: 'assistant', label: 'AI Assistant' },
      { page: 'colleagues', href: '/colleagues', icon: 'profile', label: 'Colleagues & Chat' }
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
  alert('How to use AITSM\n1) Create a ticket with clear issue details.\n2) Track status in My Tickets.\n3) Close resolved tickets, cancel active tickets, or request follow-up after 24h unresolved.');
}

function closeUserHeaderMenu() {
  document.getElementById('userHeaderMenu')?.classList.add('hidden');
}

function toggleUserHeaderMenu() {
  document.getElementById('userHeaderMenu')?.classList.toggle('hidden');
}
function toggleUserNotifMenu() { document.getElementById('userNotifMenu')?.classList.toggle('hidden'); }

function toggleUserSidebar(forceHidden) {
  const hideSidebar = typeof forceHidden === 'boolean'
    ? forceHidden
    : !document.body.classList.contains('sidebar-hidden');
  document.body.classList.toggle('sidebar-hidden', hideSidebar);
  const toggle = document.getElementById('userSidebarToggle');
  if (toggle) toggle.setAttribute('aria-expanded', hideSidebar ? 'false' : 'true');
}

async function loadUserHeaderNotifications() {
  return window.AITSMNotifications?.refresh('user');
}

async function markUserNotificationRead(id) {
  return window.AITSMNotifications?.refresh('user');
}
async function markAllUserNotificationsRead() {
  return window.AITSMNotifications?.markAll('user');
}
async function openUserNotification(id, type, relatedTicketId = 0, relatedUserId = 0) {
  return loadUserHeaderNotifications();
}

function mountUserNotifications() {
  const options = { portal: 'user', listId: 'userNotifList', countId: 'userNotifCount', menuId: 'userNotifMenu', triggerId: 'userNotifTrigger' };
  if (window.AITSMNotifications) return window.AITSMNotifications.mount(options);
  let script = document.querySelector('script[data-aitsm-notifications]');
  if (!script) {
    script = document.createElement('script');
    script.src = '/modules/common/js/notification-center.js';
    script.dataset.aitsmNotifications = 'true';
    document.body.appendChild(script);
  }
  script.addEventListener('load', () => window.AITSMNotifications?.mount(options), { once: true });
}

function renderUserUtilityHeader() {
  const host = document.getElementById('utilityHeader');
  if (!host) return;

  host.innerHTML = `
    <div class='utility-left'>
      <button id='userSidebarToggle' class='btn btn-ghost icon-btn header-icon-btn' type='button' aria-label='Toggle sidebar' title='Toggle sidebar' aria-expanded='true'>${userIcon('menu')}</button>
      <a class='utility-brand' href='/dashboard-user' aria-label='Go to user dashboard overview'>AITSM Portal</a>
    </div>
    <div class='utility-right'>
      <button class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='openGlobalSearch()' aria-label='Search AITSM' title='Search AITSM (Ctrl+K)'>⌕</button>
      <button id='userChatTrigger' class='btn btn-ghost icon-btn header-icon-btn notif-trigger-btn' type='button' onclick='openAitsmChat()' aria-label='Open chat' title='Messages' aria-expanded='false'>${userIcon('chat')}<span id='userChatCount' class='notif-count hidden'>0</span></button>
      <button id='userNotifTrigger' class='btn btn-ghost icon-btn header-icon-btn notif-trigger-btn' type='button' onclick='toggleUserNotifMenu()' aria-label='Notifications' title='Notifications'>${userIcon('bell')}<span id='userNotifCount' class='notif-count hidden'>0</span></button>
      <div id='userNotifMenu' class='admin-header-menu hidden'><div id='userNotifList' class='small'>Loading...</div></div>
      <button id='userMenuTrigger' class='btn btn-ghost icon-btn header-icon-btn' type='button' onclick='toggleUserHeaderMenu()' aria-label='Open account menu' title='Account menu'>${userIcon('more')}</button>
      <div id='userHeaderMenu' class='admin-header-menu hidden'>
        <a class='menu-action-btn' href='/profile'>${userIcon('profile')}<span>Profile</span></a>
        <button type='button' class='menu-action-btn' onclick='openUserHelp()'>${userIcon('help')}<span>Help</span></button>
        <button type='button' class='menu-action-btn' onclick='openPasswordModal()'>${userIcon('password')}<span>Change password</span></button>
        <a class='menu-action-btn' href='/security'>${userIcon('password')}<span>Security & sessions</span></a>
        <button type='button' class='menu-action-btn danger' onclick='logout()'>${userIcon('logout')}<span>Logout</span></button>
      </div>
    </div>`;

  document.getElementById('userSidebarToggle')?.addEventListener('click', (event) => {
    event.preventDefault();
    event.stopPropagation();
    toggleUserSidebar();
  });

  document.addEventListener('click', (event) => {
    const trigger = document.getElementById('userMenuTrigger');
    const menu = document.getElementById('userHeaderMenu');
    const notifBtn = document.getElementById('userNotifTrigger');
    const notifMenu = document.getElementById('userNotifMenu');
    if (menu && trigger && !(menu.contains(event.target) || trigger.contains(event.target))) closeUserHeaderMenu();
    if (notifMenu && notifBtn && !(notifMenu.contains(event.target) || notifBtn.contains(event.target))) notifMenu.classList.add('hidden');
  });

  mountUserNotifications();
}

function initializeUserShell() {
  const sidebar = document.getElementById('userSidebar');
  const header = document.getElementById('utilityHeader');
  if (!sidebar || !header) return false;
  if (window.__aitsmUserShellInitialized && sidebar.childElementCount && header.childElementCount) return true;
  renderUserSidebar();
  renderUserUtilityHeader();
  window.__aitsmUserShellInitialized = true;
  if (window.matchMedia('(max-width: 980px)').matches) toggleUserSidebar(true);
  return Boolean(sidebar.childElementCount && header.childElementCount);
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializeUserShell, { once: true });
else initializeUserShell();
