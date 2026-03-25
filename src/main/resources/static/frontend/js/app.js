const apiBase = '';

function currentRole() {
  return localStorage.getItem('role') || '';
}

const authHeaders = () => ({
  'Content-Type': 'application/json',
  'X-User-Id': localStorage.getItem('userId') || '',
  'X-User-Role': currentRole()
});

function logout() {
  localStorage.clear();
  location.href = '/login.html';
}

function redirectForRole(role) {
  location.href = ['admin', 'superadmin'].includes(role) ? '/dashboard-admin.html' : '/dashboard-user.html';
}

function enforcePageAccess() {
  const role = currentRole();
  const page = location.pathname.split('/').pop() || 'index.html';
  const adminOnlyPages = ['dashboard-admin.html', 'ticket-management.html', 'monitoring.html', 'assets.html', 'settings.html', 'user-management.html', 'knowledge.html'];
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
    <button class='btn btn-ghost icon-btn' type='button' onclick='logout()' aria-label='Logout' title='Logout'>⎋</button>`;

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
