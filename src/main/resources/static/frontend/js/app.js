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
  const adminOnlyPages = ['dashboard-admin.html', 'monitoring.html', 'assets.html', 'settings.html', 'user-management.html', 'knowledge.html'];
  const endUserOnlyPages = ['dashboard-user.html', 'create-ticket.html', 'tickets.html', 'signup.html'];

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

function markActiveNav() {
  const path = location.pathname.split('/').pop();
  document.querySelectorAll('.nav-item[data-page]').forEach(el => {
    if (el.dataset.page === path) el.classList.add('active');
  });
}

document.addEventListener('DOMContentLoaded', () => {
  enforcePageAccess();
  markActiveNav();
});
