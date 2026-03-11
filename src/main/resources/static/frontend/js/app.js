const apiBase = '';
const authHeaders = () => ({
  'Content-Type': 'application/json',
  'X-User-Id': localStorage.getItem('userId') || '',
  'X-User-Role': localStorage.getItem('role') || ''
});

function logout() {
  localStorage.clear();
  location.href = '/login.html';
}

function markActiveNav() {
  const path = location.pathname.split('/').pop();
  document.querySelectorAll('.nav-item[data-page]').forEach(el => {
    if (el.dataset.page === path) el.classList.add('active');
  });
}

document.addEventListener('DOMContentLoaded', markActiveNav);
