const apiBase = '';

function aitsmEscapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[char]));
}

function normalizeRole(role) {
  return String(role || '').toLowerCase().replaceAll('_', '-');
}

function currentRole() {
  return normalizeRole(window.currentUser?.role);
}

function isAdminPortalRole(role = currentRole()) {
  return ['admin', 'it-admin', 'superadmin'].includes(normalizeRole(role));
}

function readCookie(name) {
  const prefix = `${encodeURIComponent(name)}=`;
  return document.cookie.split(';').map(v => v.trim()).find(v => v.startsWith(prefix))?.slice(prefix.length) || '';
}

const authHeaders = () => ({ 'Content-Type': 'application/json', 'X-CSRF-Token': decodeURIComponent(readCookie('aitsm_csrf')) });
const reauthCache = new Map();
let pendingReauthPassword = null;

function ensureReauthModal() {
  if (document.getElementById('reauthModal')) return;
  const modal = document.createElement('div');
  modal.id = 'reauthModal';
  modal.className = 'modal-overlay hidden';
  modal.innerHTML = `
    <section class='modal-card app-alert-card' role='dialog' aria-modal='true' aria-labelledby='reauthModalTitle'>
      <div class='card-head'>
        <div>
          <h3 id='reauthModalTitle' class='section-title'>Confirm it is you</h3>
          <p class='small'>Enter your current password before making this sensitive change.</p>
        </div>
      </div>
      <form id='reauthForm' class='form-grid single-col'>
        <div class='form-group'>
          <label for='reauthPassword'>Current password</label>
          <input id='reauthPassword' type='password' autocomplete='current-password' required>
        </div>
        <p id='reauthMessage' class='small text-danger hidden' role='alert'></p>
        <div class='inline-actions' style='justify-content:flex-end'>
          <button id='reauthCancelBtn' class='btn btn-ghost' type='button'>Cancel</button>
          <button class='btn btn-primary' type='submit'>Continue</button>
        </div>
      </form>
    </section>`;
  document.body.appendChild(modal);

  document.getElementById('reauthForm')?.addEventListener('submit', (event) => {
    event.preventDefault();
    const password = document.getElementById('reauthPassword')?.value || '';
    if (!password) return;
    finishReauthPassword(password);
  });
  document.getElementById('reauthCancelBtn')?.addEventListener('click', () => finishReauthPassword(null));
  modal.addEventListener('click', (event) => {
    if (event.target === modal) finishReauthPassword(null);
  });
  modal.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') finishReauthPassword(null);
  });
}

function finishReauthPassword(password) {
  const modal = document.getElementById('reauthModal');
  modal?.classList.remove('show');
  modal?.classList.add('hidden');
  const input = document.getElementById('reauthPassword');
  if (input) input.value = '';
  const resolve = pendingReauthPassword?.resolve;
  pendingReauthPassword = null;
  resolve?.(password);
}

function requestReauthPassword() {
  if (pendingReauthPassword) return pendingReauthPassword.promise;
  ensureReauthModal();
  let resolvePassword;
  const promise = new Promise(resolve => { resolvePassword = resolve; });
  pendingReauthPassword = { promise, resolve: resolvePassword };
  const message = document.getElementById('reauthMessage');
  message?.classList.add('hidden');
  const modal = document.getElementById('reauthModal');
  modal?.classList.remove('hidden');
  modal?.classList.add('show');
  window.setTimeout(() => document.getElementById('reauthPassword')?.focus(), 0);
  return promise;
}

async function obtainReauthToken(scope, force = false) {
  const cached = reauthCache.get(scope);
  if (!force && cached && cached.expiresAt > Date.now() + 5000) return cached.token;
  const password = await requestReauthPassword();
  if (!password) throw new Error('Reauthentication cancelled');
  const response = await fetch('/api/auth/reauth', {
    method: 'POST', headers: authHeaders(), body: JSON.stringify({ scope, password })
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    reauthCache.delete(scope);
    throw new Error(data.error || 'Reauthentication failed');
  }
  reauthCache.set(scope, { token: data.token, expiresAt: Date.parse(data.expiresAt) || Date.now() + 240000 });
  return data.token;
}

function consumeCachedReauth(scope) { reauthCache.delete(scope); }

async function loadAuthenticatedUser() {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const response = await fetch('/api/auth/me', { credentials: 'same-origin', cache: 'no-store' });
      if (response.status === 401 || response.status === 403) return null;
      if (!response.ok) throw new Error(`Session lookup failed with ${response.status}`);
      window.currentUser = await response.json();
      return window.currentUser;
    } catch (error) {
      if (attempt === 0) await new Promise(resolve => window.setTimeout(resolve, 250));
      else {
        window.__aitsmBootstrapFailed = true;
        console.error('AITSM session bootstrap failed', error);
      }
    }
  }
  return null;
}

window.aitsmReady = loadAuthenticatedUser();

function sharedStylesReady() {
  return getComputedStyle(document.documentElement).getPropertyValue('--primary').trim().length > 0;
}

async function ensureSharedStylesheet() {
  if (sharedStylesReady()) return true;
  document.querySelectorAll('link[data-aitsm-style-retry]').forEach(link => link.remove());
  const loaded = await new Promise(resolve => {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = `/shared/css/styles.css?v=frontend-recovery-v3-${Date.now()}`;
    link.dataset.aitsmStyleRetry = 'true';
    link.addEventListener('load', () => resolve(true), { once: true });
    link.addEventListener('error', () => resolve(false), { once: true });
    document.head.appendChild(link);
  });
  const ready = loaded && sharedStylesReady();
  document.body.classList.toggle('stylesheet-load-failed', !ready);
  return ready;
}

async function logout() {
  await fetch('/api/auth/logout', { method: 'POST', headers: authHeaders(), credentials: 'same-origin' }).catch(() => {});
  sessionStorage.removeItem('aitsm.chat.drawer.v1');
  window.currentUser = null;
  location.href = '/login.html';
}

function redirectForRole(role) {
  location.href = isAdminPortalRole(role) ? '/dashboard-admin.html' : '/dashboard-user.html';
}

function enforcePageAccess() {
  const role = currentRole();
  const page = location.pathname.split('/').pop() || 'index.html';
  const adminOnlyPages = ['dashboard-admin.html', 'ticket-management.html', 'monitoring.html', 'inventory.html', 'assets.html', 'settings.html', 'user-management.html', 'knowledge.html'];
  const endUserOnlyPages = ['dashboard-user.html', 'create-ticket.html', 'tickets.html', 'knowledge-library.html', 'ai-assistant.html', 'signup.html'];

  if (!role && window.__aitsmBootstrapFailed) {
    showAppAlert('The server could not finish loading your session. Check the connection and refresh this page.');
    return;
  }

  if (!role && page !== 'login.html' && page !== 'index.html' && page !== 'signup.html') {
    location.href = '/login.html';
    return;
  }

  if (isAdminPortalRole(role) && endUserOnlyPages.includes(page)) {
    location.href = '/dashboard-admin.html';
    return;
  }

  if (!isAdminPortalRole(role) && adminOnlyPages.includes(page)) {
    location.href = '/dashboard-user.html';
  }
}

async function mountRoleAwareShell(hostId) {
  const host = document.getElementById(hostId);
  if (!host) return;
  await window.aitsmReady;
  if (!window.currentUser) return;

  const admin = isAdminPortalRole();
  host.id = admin ? 'adminSidebar' : 'userSidebar';
  await ensureRoleShell(admin);
}

function shellInitializer(admin) {
  return window[admin ? 'initializeAdminShell' : 'initializeUserShell'];
}

async function ensureRoleShell(admin = isAdminPortalRole()) {
  const expectedId = admin ? 'adminSidebar' : 'userSidebar';
  const host = document.getElementById(expectedId) || document.querySelector('.app-layout > .sidebar');
  if (!host) return true;
  host.id = expectedId;

  const initialize = shellInitializer(admin);
  if (typeof initialize === 'function') {
    initialize();
    if (host.childElementCount && document.getElementById('utilityHeader')?.childElementCount) return true;
  }

  const shellName = admin ? 'admin-shell' : 'user-shell';
  document.querySelectorAll(`script[src*="/modules/sidebar/js/${shellName}.js"]`).forEach(script => script.remove());
  const loaded = await new Promise(resolve => {
    const script = document.createElement('script');
    script.src = `/modules/sidebar/js/${shellName}.js?v=workspace-shell-v2`;
    script.dataset.aitsmRoleShell = 'true';
    script.addEventListener('load', () => resolve(true), { once: true });
    script.addEventListener('error', () => resolve(false), { once: true });
    document.body.appendChild(script);
  });

  const retry = shellInitializer(admin);
  if (loaded && typeof retry === 'function') retry();
  const ready = Boolean(host.childElementCount && document.getElementById('utilityHeader')?.childElementCount);
  document.body.classList.toggle('shell-load-failed', !ready);
  if (!ready) showAppAlert('Navigation could not be loaded. Check the server connection and refresh the page.');
  return ready;
}

async function ensureWorkspaceShell() {
  await window.aitsmReady;
  if (!window.currentUser) return;
  await ensureRoleShell(isAdminPortalRole());
}

function injectGlobalHeader() {
  const role = currentRole();
  if (!role) return;
  const content = document.querySelector('main.content');
  if (!content || document.getElementById('globalAppHeader') || document.getElementById('utilityHeader')) return;

  const userEmail = window.currentUser?.email || '';
  const roleLabel = role === 'superadmin' ? 'Super Admin' : (isAdminPortalRole(role) ? 'Admin' : 'End User');

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
    <section class='modal-card' role='dialog' aria-modal='true' aria-labelledby='passwordModalTitle'>
      <div class='card-head'><h3 id='passwordModalTitle' class='section-title'>Change Password</h3></div>
      <form id='passwordChangeForm' class='form-grid single-col'>
        <div class='form-group'>
          <label for='modalCurrentPassword'>Current password</label>
          <input id='modalCurrentPassword' type='password' autocomplete='current-password' required>
        </div>
        <div class='form-group'>
          <label for='modalNewPassword'>New password</label>
          <input id='modalNewPassword' type='password' autocomplete='new-password' minlength='12' required aria-describedby='passwordRequirements'>
          <span id='passwordRequirements' class='small'>Use at least 12 characters.</span>
        </div>
        <div class='form-group'>
          <label for='modalConfirmPassword'>Confirm new password</label>
          <input id='modalConfirmPassword' type='password' autocomplete='new-password' minlength='12' required>
        </div>
        <p id='passwordChangeMessage' class='small hidden' role='status' aria-live='polite'></p>
        <div class='inline-actions' style='justify-content:flex-end'>
          <button id='passwordChangeCancel' type='button' class='btn btn-ghost'>Cancel</button>
          <button id='passwordChangeSubmit' type='submit' class='btn btn-primary'>Save password</button>
        </div>
      </form>
    </section>`;
  document.body.appendChild(modal);

  document.getElementById('passwordChangeForm')?.addEventListener('submit', submitPasswordChange);
  document.getElementById('passwordChangeCancel')?.addEventListener('click', closePasswordModal);
  modal.addEventListener('click', (event) => {
    if (event.target?.id === 'passwordModal') closePasswordModal();
  });
  modal.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closePasswordModal();
  });
}

function setPasswordChangeMessage(message = '', tone = 'danger') {
  const target = document.getElementById('passwordChangeMessage');
  if (!target) return;
  target.textContent = message;
  target.classList.toggle('hidden', !message);
  target.classList.toggle('text-danger', Boolean(message) && tone === 'danger');
  target.classList.toggle('text-success', Boolean(message) && tone === 'success');
  target.setAttribute('role', tone === 'danger' ? 'alert' : 'status');
}

function openPasswordModal() {
  ensurePasswordModal();
  setPasswordChangeMessage('');
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
  setPasswordChangeMessage('');
  const submit = document.getElementById('passwordChangeSubmit');
  if (submit) {
    submit.disabled = false;
    submit.textContent = 'Save password';
  }
}

async function submitPasswordChange(event) {
  event?.preventDefault();
  const body = {
    currentPassword: document.getElementById('modalCurrentPassword')?.value || '',
    newPassword: document.getElementById('modalNewPassword')?.value || '',
    confirmPassword: document.getElementById('modalConfirmPassword')?.value || ''
  };

  if (!body.currentPassword) return setPasswordChangeMessage('Enter your current password.');
  if (body.newPassword.length < 12) return setPasswordChangeMessage('New password must be at least 12 characters.');
  if (body.newPassword === body.currentPassword) return setPasswordChangeMessage('New password must be different from the current password.');
  if (body.newPassword !== body.confirmPassword) return setPasswordChangeMessage('New password and confirmation do not match.');

  const submit = document.getElementById('passwordChangeSubmit');
  if (submit?.disabled) return;
  if (submit) {
    submit.disabled = true;
    submit.textContent = 'Saving…';
  }
  setPasswordChangeMessage('');

  try {
    const res = await fetch('/api/users/me/password', {
      method: 'PUT',
      headers: authHeaders(),
      credentials: 'same-origin',
      body: JSON.stringify(body)
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      setPasswordChangeMessage(data.error || data.message || 'Unable to change password. Please try again.');
      return;
    }
    closePasswordModal();
    showAppAlert(data.message || 'Password changed successfully.');
  } catch {
    setPasswordChangeMessage('Unable to reach the server. Check your connection and try again.');
  } finally {
    if (submit) {
      submit.disabled = false;
      submit.textContent = 'Save password';
    }
  }
}

document.addEventListener('DOMContentLoaded', async () => {
  await ensureSharedStylesheet();
  ensurePageSplash();
  await window.aitsmReady;
  enforcePageAccess();
  await ensureWorkspaceShell();
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
  const admin = isAdminPortalRole();
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
