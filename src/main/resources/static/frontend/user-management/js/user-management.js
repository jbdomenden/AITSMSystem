const userMgmtState = {
  users: [],
  filters: {
    search: '',
    role: 'all',
    verification: 'all',
    sort: 'name-asc'
  }
};

function ensureActionMenuBackdrop() {
  if (document.getElementById('actionMenuBackdrop')) return;
  const backdrop = document.createElement('div');
  backdrop.id = 'actionMenuBackdrop';
  backdrop.className = 'action-menu-backdrop hidden';
  backdrop.addEventListener('click', () => {
    document.querySelectorAll('.row-action-menu').forEach((el) => el.classList.add('hidden'));
    document.getElementById('actionMenuBackdrop')?.classList.add('hidden');
    backdrop.classList.add('hidden');
  });
  document.body.appendChild(backdrop);
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function closeUserMgmtMenus() {
  document.querySelectorAll('.row-action-menu').forEach((el) => el.classList.add('hidden'));
  document.getElementById('actionMenuBackdrop')?.classList.add('hidden');
}

function toggleUserMgmtMenu(event, id) {
  event.stopPropagation();
  const menu = document.getElementById(`userMgmtMenu-${id}`);
  if (!menu) return;
  const trigger = event.currentTarget;
  const open = menu.classList.contains('hidden');
  closeUserMgmtMenus();
  if (!open) return;
  ensureActionMenuBackdrop();
  document.getElementById('actionMenuBackdrop')?.classList.remove('hidden');
  const rect = trigger.getBoundingClientRect();
  menu.style.position = 'fixed';
  menu.style.top = `${rect.bottom + 4}px`;
  menu.style.left = `${Math.max(8, rect.right - 200)}px`;
  menu.classList.remove('hidden');
}

async function fetchJsonOrThrow(url, options = {}) {
  const res = await fetch(url, { headers: authHeaders(), ...options });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || `Failed request: ${url}`);
  return data;
}

async function changeRoleFromUserManagement(userId, role) {
  const res = await fetch(`/api/users/${userId}/role`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ role })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to update role');
  await loadUserManagement();
}

async function resetUserPasswordFromUserManagement(userId, email) {
  const newPassword = prompt(`Set a temporary password for ${email}:`);
  if (!newPassword) return;
  const confirmPassword = prompt('Confirm the temporary password:');
  if (confirmPassword == null) return;

  const res = await fetch(`/api/users/${userId}/reset-password`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ newPassword, confirmPassword })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to reset password');
  alert(data.message || 'Password reset successful');
}

async function deleteUserFromUserManagement(userId, email) {
  const confirmed = confirm(`Delete account ${email}? This also removes user tickets and notifications.`);
  if (!confirmed) return;

  const res = await fetch(`/api/users/${userId}`, {
    method: 'DELETE',
    headers: authHeaders()
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to delete user');

  alert(data.message || 'User deleted');
  await loadUserManagement();
}

async function setUserEmailApprovalFromUserManagement(userId, approved) {
  const res = await fetch(`/api/users/${userId}/email-approval`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ approved })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to update email approval');
  await loadUserManagement();
}

async function openAddAdminFromUserManagement(targetEmail) {
  const password = prompt(`Verify your password to grant admin role to ${targetEmail}:`);
  if (!password) return;

  const verifyRes = await fetch('/api/users/admin/verify', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ password })
  });
  const verifyData = await verifyRes.json();
  if (!verifyRes.ok || !verifyData.verified || !verifyData.verificationToken) {
    return alert(verifyData.message || verifyData.error || 'Verification failed');
  }

  const grantRes = await fetch('/api/users/admin/grant', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ targetEmail, verificationToken: verifyData.verificationToken })
  });
  const grantData = await grantRes.json();
  if (!grantRes.ok || !grantData.success) return alert(grantData.message || grantData.error || 'Unable to grant admin role');

  alert(grantData.message || 'Admin role granted successfully');
  await loadUserManagement();
}

function userSortComparator(sortKey) {
  if (sortKey === 'name-desc') return (a, b) => String(b.fullName || '').localeCompare(String(a.fullName || ''));
  if (sortKey === 'email-asc') return (a, b) => String(a.email || '').localeCompare(String(b.email || ''));
  if (sortKey === 'email-desc') return (a, b) => String(b.email || '').localeCompare(String(a.email || ''));
  if (sortKey === 'role-asc') return (a, b) => String(a.role || '').localeCompare(String(b.role || ''));
  return (a, b) => String(a.fullName || '').localeCompare(String(b.fullName || ''));
}

function applyUserManagementFilters() {
  const query = userMgmtState.filters.search.trim().toLowerCase();
  const role = userMgmtState.filters.role;
  const verification = userMgmtState.filters.verification;

  const filtered = userMgmtState.users.filter((user) => {
    const userRole = String(user.role || '').toLowerCase();
    const isVerified = Boolean(user.emailVerified);

    const roleMatch = role === 'all' || userRole === role;
    const verificationMatch = verification === 'all'
      || (verification === 'verified' && isVerified)
      || (verification === 'pending' && !isVerified);

    if (!query) return roleMatch && verificationMatch;

    const tokens = [user.fullName, user.email, user.role].map((value) => String(value || '').toLowerCase());
    return roleMatch && verificationMatch && tokens.some((token) => token.includes(query));
  });

  return [...filtered].sort(userSortComparator(userMgmtState.filters.sort));
}

function renderUserManagementCount(visible, total) {
  const el = document.getElementById('userMgmtCount');
  if (!el) return;
  el.textContent = `${visible} shown / ${total} total`;
}

function renderUserManagementRows() {
  const rows = document.getElementById('userMgmtRows');
  if (!rows) return;

  const users = applyUserManagementFilters();
  renderUserManagementCount(users.length, userMgmtState.users.length);

  if (!users.length) {
    renderTableEmptyState(rows, 5, 'No users matched the selected filters.');
    return;
  }

  rows.innerHTML = users.map((u) => {
    const currentUserId = Number(localStorage.getItem('userId') || 0);
    const canChange = u.role !== 'superadmin' && u.id !== currentUserId;
    const encodedEmail = encodeURIComponent(u.email || '');
    const menuItems = [
      u.role === 'admin'
        ? `<button type='button' ${canChange ? '' : 'disabled'} onclick='changeRoleFromUserManagement(${u.id}, "end-user")'>Set as end-user</button>`
        : `<button type='button' ${canChange ? '' : 'disabled'} onclick='openAddAdminFromUserManagement(decodeURIComponent("${encodedEmail}"))'>Grant admin role</button>`,
      `<button type='button' ${canChange ? '' : 'disabled'} onclick='setUserEmailApprovalFromUserManagement(${u.id}, ${u.emailVerified ? 'false' : 'true'})'>${u.emailVerified ? 'Mark email as pending' : 'Approve email for login'}</button>`,
      `<button type='button' ${canChange ? '' : 'disabled'} onclick='resetUserPasswordFromUserManagement(${u.id}, decodeURIComponent("${encodedEmail}"))'>Reset password</button>`,
      `<button type='button' ${canChange ? '' : 'disabled'} onclick='deleteUserFromUserManagement(${u.id}, decodeURIComponent("${encodedEmail}"))'>Delete account</button>`
    ].join('');

    const actionMenu = `<div class='row-action-wrap'>
      <button class='btn btn-ghost icon-btn' onclick='toggleUserMgmtMenu(event, ${u.id})' title='Actions' aria-label='Actions'>...</button>
      <div id='userMgmtMenu-${u.id}' class='row-action-menu hidden'>${menuItems}</div>
    </div>`;

    return `<tr>
      <td>${escapeHtml(u.fullName)}</td>
      <td>${escapeHtml(u.email)}</td>
      <td><span class='badge ${u.role === 'admin' || u.role === 'superadmin' ? 'in-progress' : 'resolved'}'>${escapeHtml(u.role)}</span></td>
      <td>${u.emailVerified ? '<span class="badge resolved">Verified</span>' : '<span class="badge warning">Pending</span>'}</td>
      <td>${actionMenu}</td>
    </tr>`;
  }).join('');
}

async function loadUserManagement() {
  const rows = document.getElementById('userMgmtRows');
  if (!rows) return;
  showTableSkeleton(rows, { rowCount: 6, columnCount: 5, hasActions: true });

  try {
    userMgmtState.users = await fetchJsonOrThrow('/api/users');
    clearTableSkeleton(rows);
    renderUserManagementRows();
  } catch (error) {
    renderTableErrorState(rows, 5, error.message);
  } finally {
    clearTableSkeleton(rows);
  }
}

function wireUserManagementFilters() {
  const search = document.getElementById('userMgmtSearch');
  if (search) {
    search.addEventListener('input', () => {
      userMgmtState.filters.search = search.value || '';
      renderUserManagementRows();
    });
  }

  const bindSelect = (id, key) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('change', () => {
      userMgmtState.filters[key] = el.value || 'all';
      renderUserManagementRows();
    });
  };

  bindSelect('userMgmtRoleFilter', 'role');
  bindSelect('userMgmtVerificationFilter', 'verification');
  bindSelect('userMgmtSort', 'sort');
}

function setCreateUserMessage(message, tone = 'info') {
  const el = document.getElementById('createUserMessage');
  if (!el) return;
  el.textContent = message || '';
  el.classList.remove('text-success', 'text-danger');
  if (tone === 'success') el.classList.add('text-success');
  if (tone === 'danger') el.classList.add('text-danger');
}

function openCreateUserModal() {
  const modal = document.getElementById('createUserModal');
  if (!modal) return;
  modal.classList.remove('hidden');
  modal.classList.add('show');
  setCreateUserMessage('');
  document.getElementById('internalFullName')?.focus();
}

function closeCreateUserModal() {
  const modal = document.getElementById('createUserModal');
  if (!modal) return;
  modal.classList.remove('show');
  modal.classList.add('hidden');
  document.getElementById('createUserForm')?.reset();
  const verified = document.getElementById('internalEmailVerified');
  if (verified) verified.checked = true;
  setCreateUserMessage('');
}

function validateCreateUserPayload(payload) {
  if (!payload.fullName) return 'Full name is required.';
  if (!payload.email) return 'Email is required.';
  if (!payload.company) return 'Company is required.';
  if (!payload.department) return 'Department is required.';
  if (payload.password.length < 8) return 'Temporary password must be at least 8 characters.';
  if (payload.password !== payload.confirmPassword) return 'Password and confirmation do not match.';
  return null;
}

async function submitCreateUser(event) {
  event.preventDefault();
  const payload = {
    fullName: document.getElementById('internalFullName')?.value?.trim() || '',
    email: document.getElementById('internalEmail')?.value?.trim() || '',
    company: document.getElementById('internalCompany')?.value?.trim() || '',
    department: document.getElementById('internalDepartment')?.value?.trim() || '',
    password: document.getElementById('internalPassword')?.value || '',
    confirmPassword: document.getElementById('internalConfirmPassword')?.value || '',
    role: document.getElementById('internalRole')?.value || 'end-user',
    emailVerified: Boolean(document.getElementById('internalEmailVerified')?.checked)
  };

  const validationError = validateCreateUserPayload(payload);
  if (validationError) {
    setCreateUserMessage(validationError, 'danger');
    return;
  }

  setCreateUserMessage('Creating account...');
  const res = await fetch('/api/users', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(payload)
  });
  const data = await res.json();
  if (!res.ok) {
    setCreateUserMessage(data.error || 'Unable to create account', 'danger');
    return;
  }

  setCreateUserMessage(data.message || 'User account created', 'success');
  await loadUserManagement();
  setTimeout(() => closeCreateUserModal(), 900);
}

document.addEventListener('click', () => closeUserMgmtMenus());

document.addEventListener('DOMContentLoaded', () => {
  wireUserManagementFilters();
  loadUserManagement();
  document.getElementById('openCreateUserBtn')?.addEventListener('click', openCreateUserModal);
  document.getElementById('createUserForm')?.addEventListener('submit', submitCreateUser);
  document.getElementById('createUserModal')?.addEventListener('click', (e) => {
    if (e.target.id === 'createUserModal') closeCreateUserModal();
  });
});
