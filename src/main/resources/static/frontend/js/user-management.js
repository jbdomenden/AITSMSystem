
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

async function loadUserManagement() {
  const rows = document.getElementById('userMgmtRows');
  if (!rows) return;

  rows.innerHTML = "<tr><td colspan='5' class='small'>Loading users...</td></tr>";

  try {
    const users = await fetchJsonOrThrow('/api/users');
    rows.innerHTML = users.map(u => {
      const currentUserId = Number(localStorage.getItem('userId') || 0);
      const canChange = u.role !== 'superadmin' && u.id !== currentUserId;
      const menuItems = [
        u.role === 'admin'
          ? `<button type='button' ${canChange ? '' : 'disabled'} onclick='changeRoleFromUserManagement(${u.id}, "end-user")'>Set as end-user</button>`
          : `<button type='button' ${canChange ? '' : 'disabled'} onclick='openAddAdminFromUserManagement("${u.email}")'>Grant admin role</button>`,
        `<button type='button' ${canChange ? '' : 'disabled'} onclick='setUserEmailApprovalFromUserManagement(${u.id}, ${u.emailVerified ? 'false' : 'true'})'>${u.emailVerified ? 'Mark email as pending' : 'Approve email for login'}</button>`,
        `<button type='button' ${canChange ? '' : 'disabled'} onclick='resetUserPasswordFromUserManagement(${u.id}, "${u.email}")'>Reset password</button>`,
        `<button type='button' ${canChange ? '' : 'disabled'} onclick='deleteUserFromUserManagement(${u.id}, "${u.email}")'>Delete account</button>`
      ].join('');
      const actionMenu = `<div class='row-action-wrap'>
        <button class='btn btn-ghost icon-btn' onclick='toggleUserMgmtMenu(event, ${u.id})' title='Actions' aria-label='Actions'>⋯</button>
        <div id='userMgmtMenu-${u.id}' class='row-action-menu hidden'>${menuItems}</div>
      </div>`;

      return `<tr>
        <td>${u.fullName}</td>
        <td>${u.email}</td>
        <td><span class='badge ${u.role === 'admin' || u.role === 'superadmin' ? 'in-progress' : 'resolved'}'>${u.role}</span></td>
        <td>${u.emailVerified ? '<span class="badge resolved">Verified</span>' : '<span class="badge warning">Pending</span>'}</td>
        <td>${actionMenu}</td>
      </tr>`;
    }).join('');
  } catch (error) {
    rows.innerHTML = `<tr><td colspan='5' class='small text-danger'>${error.message}</td></tr>`;
  }
}

document.addEventListener('click', () => closeUserMgmtMenus());

document.addEventListener('DOMContentLoaded', () => {
  loadUserManagement();
  document.getElementById('openCreateUserBtn')?.addEventListener('click', openCreateUserModal);
  document.getElementById('createUserForm')?.addEventListener('submit', submitCreateUser);
  document.getElementById('createUserModal')?.addEventListener('click', (e) => {
    if (e.target.id === 'createUserModal') closeCreateUserModal();
  });
});


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
