const apiBase = '';

function normalizeRole(role) {
  return String(role || '').toLowerCase();
}

function currentRole() {
  return normalizeRole(localStorage.getItem('role'));
}

const authHeaders = () => ({
  'Content-Type': 'application/json',
  'X-User-Id': localStorage.getItem('userId') || '',
  'X-User-Role': currentRole()
});

function logout() {
  sessionStorage.removeItem('aitsm.chat.drawer.v1');
  localStorage.clear();
  location.href = '/login.html';
}

function redirectForRole(role) {
  const normalizedRole = normalizeRole(role);
  location.href = ['admin', 'superadmin'].includes(normalizedRole) ? '/dashboard-admin.html' : '/dashboard-user.html';
}

function enforcePageAccess() {
  const role = currentRole();
  const page = location.pathname.split('/').pop() || 'index.html';
  const adminOnlyPages = ['dashboard-admin.html', 'ticket-management.html', 'monitoring.html', 'inventory.html', 'assets.html', 'settings.html', 'user-management.html', 'knowledge.html'];
  const endUserOnlyPages = ['dashboard-user.html', 'create-ticket.html', 'tickets.html', 'knowledge-library.html', 'ai-assistant.html', 'signup.html'];

  if (!role && page !== 'login.html' && page !== 'index.html' && page !== 'signup.html') {
    location.href = '/login.html';
    return;
  }

  if (['admin', 'superadmin'].includes(role) && endUserOnlyPages.includes(page)) {
    location.href = '/dashboard-admin.html';
    return;
  }

  if (!['admin', 'superadmin'].includes(role) && adminOnlyPages.includes(page)) {
    location.href = '/dashboard-user.html';
  }
}

function injectGlobalHeader() {
  const role = currentRole();
  if (!role) return;
  const content = document.querySelector('main.content');
  if (!content || document.getElementById('globalAppHeader') || document.getElementById('utilityHeader')) return;

  const userEmail = localStorage.getItem('email') || '';
  const roleLabel = role === 'superadmin' ? 'Super Admin' : (role === 'admin' ? 'Admin' : 'End User');

  const header = document.createElement('header');
  header.id = 'globalAppHeader';
  header.className = 'global-header card';
  header.innerHTML = `
    <div>
      <h2 class='section-title'>AITSM Portal</h2>
      <p class='small'>${roleLabel}${userEmail ? ` • ${userEmail}` : ''}</p>
    </div>
    <div class='inline-actions'>
      <button id='globalChatTrigger' class='btn btn-ghost icon-btn notif-trigger-btn' type='button' onclick='openAitsmChat()' aria-label='Open chat' title='Messages' aria-expanded='false'>💬<span id='globalChatCount' class='notif-count hidden'>0</span></button>
      <button class='btn btn-ghost icon-btn' type='button' onclick='logout()' aria-label='Logout' title='Logout'>⎋</button>
    </div>`;

  content.prepend(header);
}

function ensurePageSplash() {
  if (document.getElementById('pageSplash')) return;
  const splash = document.createElement('div');
  splash.id = 'pageSplash';
  splash.className = 'page-splash';
  splash.innerHTML = `
    <div class='page-splash-card'>
      <div class='page-splash-spinner' aria-hidden='true'></div>
      <h3>Loading AITSM</h3>
      <p class='small'>Preparing page and buffering analytics...</p>
    </div>`;
  document.body.appendChild(splash);
}

function hidePageSplash() {
  const splash = document.getElementById('pageSplash');
  if (!splash) return;
  splash.classList.add('page-splash-hidden');
  window.setTimeout(() => splash.remove(), 220);
}

function markActiveNav() {
  const path = location.pathname.split('/').pop();
  document.querySelectorAll('.nav-item[data-page]').forEach(el => {
    if (el.dataset.page === path) el.classList.add('active');
  });
}

function ensurePasswordModal() {
  if (document.getElementById('passwordModal')) return;
  const modal = document.createElement('div');
  modal.id = 'passwordModal';
  modal.className = 'modal-overlay hidden';
  modal.innerHTML = `
    <div class='modal-card'>
      <div class='card-head'><h3 class='section-title'>Change Password</h3></div>
      <div class='form-grid single-col'>
        <div class='form-group'><label>Current Password</label><input id='modalCurrentPassword' type='password'></div>
        <div class='form-group'><label>New Password</label><input id='modalNewPassword' type='password'></div>
        <div class='form-group'><label>Confirm New Password</label><input id='modalConfirmPassword' type='password'></div>
      </div>
      <div class='inline-actions' style='justify-content:flex-end'>
        <button type='button' class='btn btn-ghost' onclick='closePasswordModal()'>Cancel</button>
        <button type='button' class='btn btn-primary' onclick='submitPasswordChange()'>Save</button>
      </div>
    </div>`;
  document.body.appendChild(modal);

  modal.addEventListener('click', (event) => {
    if (event.target?.id === 'passwordModal') closePasswordModal();
  });
}

function openPasswordModal() {
  ensurePasswordModal();
  const modal = document.getElementById('passwordModal');
  modal?.classList.remove('hidden');
  modal?.classList.add('show');
  document.getElementById('modalCurrentPassword')?.focus();
}

function closePasswordModal() {
  const modal = document.getElementById('passwordModal');
  modal?.classList.remove('show');
  modal?.classList.add('hidden');
  ['modalCurrentPassword', 'modalNewPassword', 'modalConfirmPassword'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
}

async function submitPasswordChange() {
  const body = {
    currentPassword: document.getElementById('modalCurrentPassword')?.value || '',
    newPassword: document.getElementById('modalNewPassword')?.value || '',
    confirmPassword: document.getElementById('modalConfirmPassword')?.value || ''
  };

  const res = await fetch('/api/users/me/password', {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to change password');
  alert(data.message || 'Password changed successfully');
  closePasswordModal();
}

document.addEventListener('DOMContentLoaded', () => {
  ensurePageSplash();
  enforcePageAccess();
  injectGlobalHeader();
  markActiveNav();
  ensureChatDrawer();
  window.setTimeout(hidePageSplash, 450);
});

window.addEventListener('load', hidePageSplash);

function ensureAppAlertModal() {
  if (document.getElementById('appAlertModal')) return;
  const modal = document.createElement('div');
  modal.id = 'appAlertModal';
  modal.className = 'modal-overlay hidden';
  modal.innerHTML = `
    <div class='modal-card app-alert-card'>
      <div class='card-head'>
        <h3 class='section-title'>Notice</h3>
      </div>
      <p id='appAlertMessage' class='small app-alert-message'></p>
      <div class='inline-actions' style='justify-content:flex-end'>
        <button id='appAlertOkBtn' class='btn btn-primary' type='button'>OK</button>
      </div>
    </div>`;
  document.body.appendChild(modal);

  document.getElementById('appAlertOkBtn')?.addEventListener('click', closeAppAlert);
  modal.addEventListener('click', (event) => {
    if (event.target?.id === 'appAlertModal') closeAppAlert();
  });
}

function showAppAlert(message) {
  ensureAppAlertModal();
  const modal = document.getElementById('appAlertModal');
  const messageEl = document.getElementById('appAlertMessage');
  if (messageEl) messageEl.textContent = String(message ?? '');
  modal?.classList.remove('hidden');
  modal?.classList.add('show');
  document.getElementById('appAlertOkBtn')?.focus();
}

function closeAppAlert() {
  const modal = document.getElementById('appAlertModal');
  modal?.classList.remove('show');
  modal?.classList.add('hidden');
}

if (!window.__aitsmAlertPatched) {
  window.__aitsmAlertPatched = true;
  window.alert = (message = '') => {
    showAppAlert(message);
  };
}

const globalSearchEscape = (value) => String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]));

function closeGlobalSearch() {
  const modal = document.getElementById('globalSearchModal');
  modal?.classList.remove('show');
  modal?.classList.add('hidden');
  document.body.classList.remove('global-search-open');
}

function colleagueProfileUrl(id) {
  return `/profile.html?id=${encodeURIComponent(id)}`;
}

function colleagueChatUrl(id) {
  return `/colleagues.html?chat=${encodeURIComponent(id)}`;
}

function ensureChatDrawer() {
  if (!currentRole() || window.AITSMChat || document.querySelector('script[data-aitsm-chat]')) return;
  const script = document.createElement('script');
  script.src = '/modules/common/js/chat-drawer.js';
  script.dataset.aitsmChat = 'true';
  script.addEventListener('load', () => window.AITSMChat?.mount());
  document.body.appendChild(script);
}

function openAitsmChat(colleagueId = null) {
  const id = Number(colleagueId || 0);
  if (window.AITSMChat) {
    window.AITSMChat.open(id > 0 ? id : null);
    return;
  }
  if (id > 0) window.__aitsmPendingChatId = id;
  ensureChatDrawer();
}

function globalNavigationItems() {
  const admin = ['admin', 'superadmin'].includes(currentRole());
  return admin
    ? [{ label: 'Dashboard', href: '/dashboard-admin.html' }, { label: 'Ticket management', href: '/ticket-management.html' }, { label: 'User management', href: '/user-management.html' }, { label: 'Settings', href: '/settings.html' }, { label: 'Colleagues & Chat', href: '/colleagues.html' }, { label: 'Profile', href: '/profile.html' }]
    : [{ label: 'Dashboard', href: '/dashboard-user.html' }, { label: 'Create ticket', href: '/create-ticket.html' }, { label: 'My tickets', href: '/tickets.html' }, { label: 'Knowledge base', href: '/knowledge-library.html' }, { label: 'AI assistant', href: '/ai-assistant.html' }, { label: 'Colleagues & Chat', href: '/colleagues.html' }, { label: 'Profile', href: '/profile.html' }];
}

let globalSearchRequest = 0;
async function runGlobalSearch(query = '') {
  const target = document.getElementById('globalSearchResults');
  if (!target) return;
  const normalized = query.trim().toLowerCase();
  const requestId = ++globalSearchRequest;
  if (!normalized) {
    target.innerHTML = '<p class="small global-search-hint">Start typing to search pages and colleagues.</p>';
    return;
  }
  const pages = globalNavigationItems().filter(item => item.label.toLowerCase().includes(normalized));
  target.innerHTML = '<p class="small global-search-hint">Searching…</p>';
  let people = [];
  try {
    const response = await fetch(`/api/colleagues/search?q=${encodeURIComponent(query)}`, { headers: authHeaders() });
    if (response.ok) people = await response.json();
  } catch { /* Navigation results still work while people search is unavailable. */ }
  if (requestId !== globalSearchRequest) return;
  target.innerHTML = `${pages.length ? `<div class='global-search-section'>Pages</div>${pages.map(item => `<button class='global-search-result' type='button' data-page='${item.href}'><strong>${globalSearchEscape(item.label)}</strong></button>`).join('')}` : ''}${people.length ? `<div class='global-search-section'>Colleagues</div>${people.map(item => `<button class='global-search-result' type='button' data-profile='${item.colleague.id}'><span><strong>${globalSearchEscape(item.colleague.fullName)}</strong><br><span class='small'>${globalSearchEscape(item.colleague.department)} · ${globalSearchEscape(item.colleague.role)}</span></span></button>`).join('')}` : ''}` || '<p class="small global-search-hint">No matching pages or approved colleagues.</p>';
  target.querySelectorAll('[data-page]').forEach(button => button.addEventListener('click', () => { location.href = button.dataset.page; }));
  target.querySelectorAll('[data-profile]').forEach(button => button.addEventListener('click', () => {
    closeGlobalSearch();
    location.href = colleagueProfileUrl(button.dataset.profile);
  }));
}

function openGlobalSearch() {
  if (!currentRole()) return;
  let modal = document.getElementById('globalSearchModal');
  if (!modal) {
    modal = document.createElement('div');
    modal.id = 'globalSearchModal';
    modal.className = 'modal-overlay hidden';
    modal.innerHTML = `<div class='modal-card global-search-card' role='dialog' aria-modal='true' aria-label='Search AITSM'><div class='card-head'><h3 class='section-title'>Search AITSM</h3><button class='btn btn-ghost' type='button' data-close-search aria-label='Close search'>×</button></div><input id='globalSearchInput' class='global-search-input' type='search' autocomplete='off' placeholder='Search pages or colleagues'><div id='globalSearchResults' class='global-search-results'></div></div>`;
    document.body.appendChild(modal);
    modal.addEventListener('click', event => { if (event.target === modal) closeGlobalSearch(); });
    modal.querySelector('[data-close-search]').addEventListener('click', closeGlobalSearch);
    modal.querySelector('#globalSearchInput').addEventListener('input', event => runGlobalSearch(event.target.value));
  }
  modal.classList.remove('hidden');
  modal.classList.add('show');
  document.body.classList.add('global-search-open');
  const input = document.getElementById('globalSearchInput');
  input.value = '';
  runGlobalSearch();
  window.setTimeout(() => input.focus(), 0);
}

document.addEventListener('keydown', event => {
  const inputFocused = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName);
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); openGlobalSearch(); }
  else if (event.key === 'Escape') { closeGlobalSearch(); }
  else if (event.key === '/' && !inputFocused) { event.preventDefault(); openGlobalSearch(); }
});
