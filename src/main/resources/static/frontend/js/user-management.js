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
      const roleBtn = u.role === 'admin'
        ? `<button class='btn btn-ghost' ${canChange ? '' : 'disabled'} onclick='changeRoleFromUserManagement(${u.id}, "end-user")'>Set End-User</button>`
        : `<button class='btn btn-primary' ${canChange ? '' : 'disabled'} onclick='openAddAdminFromUserManagement("${u.email}")'>Make Admin</button>`;
      const resetBtn = `<button class='btn btn-ghost' ${canChange ? '' : 'disabled'} onclick='resetUserPasswordFromUserManagement(${u.id}, "${u.email}")'>Reset Password</button>`;
      const deleteBtn = `<button class='btn btn-ghost' ${canChange ? '' : 'disabled'} onclick='deleteUserFromUserManagement(${u.id}, "${u.email}")'>Delete</button>`;

      return `<tr>
        <td>${u.fullName}</td>
        <td>${u.email}</td>
        <td><span class='badge ${u.role === 'admin' || u.role === 'superadmin' ? 'in-progress' : 'resolved'}'>${u.role}</span></td>
        <td>${u.emailVerified ? '<span class="badge resolved">Verified</span>' : '<span class="badge warning">Pending</span>'}</td>
        <td><div class='inline-actions'>${roleBtn}${resetBtn}${deleteBtn}</div></td>
      </tr>`;
    }).join('');
  } catch (error) {
    rows.innerHTML = `<tr><td colspan='5' class='small text-danger'>${error.message}</td></tr>`;
  }
}

document.addEventListener('DOMContentLoaded', loadUserManagement);
