async function fetchJsonOrThrow(url, options = {}) {
  const res = await fetch(url, { headers: authHeaders(), ...options });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || `Failed request: ${url}`);
  return data;
}

async function loadProfileSettings() {
  const user = await fetchJsonOrThrow('/api/users/me');
  document.getElementById('settingsFullName').value = user.fullName || '';
  document.getElementById('settingsEmail').value = user.email || '';
  document.getElementById('settingsCompany').value = user.company || '';
  document.getElementById('settingsDepartment').value = user.department || '';
}

async function saveProfileSettings() {
  const body = {
    fullName: (document.getElementById('settingsFullName')?.value || '').trim(),
    company: (document.getElementById('settingsCompany')?.value || '').trim(),
    department: (document.getElementById('settingsDepartment')?.value || '').trim()
  };

  const res = await fetch('/api/users/me', {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to save profile');
  alert('Profile updated successfully.');
}

async function loadSlaPolicies() {
  const rows = document.getElementById('slaRows');
  if (!rows) return;

  const policies = await fetchJsonOrThrow('/api/sla');
  rows.innerHTML = policies.map(p => `<tr><td>${p.priority}</td><td>${p.responseTime}</td><td>${p.resolutionTime}</td></tr>`).join('')
    || "<tr><td colspan='3' class='small'>No SLA policies found.</td></tr>";
}

async function loadNotificationSummary() {
  const host = document.getElementById('settingsNotificationSummary');
  if (!host) return;

  const items = await fetchJsonOrThrow('/api/notifications');
  const total = items.length;
  const critical = items.filter(n => ['error', 'critical'].includes((n.type || '').toLowerCase())).length;
  const latest = items[0]?.createdAt ? new Date(items[0].createdAt).toLocaleString() : 'N/A';
  host.innerHTML = `Total notifications: <strong>${total}</strong><br>Critical notifications: <strong>${critical}</strong><br>Latest update: <strong>${latest}</strong>`;
}

document.addEventListener('DOMContentLoaded', async () => {
  try {
    await Promise.all([loadProfileSettings(), loadSlaPolicies(), loadNotificationSummary()]);
  } catch (error) {
    alert(error.message || 'Unable to load settings data');
  }
});


async function changeMyPassword() {
  const body = {
    currentPassword: document.getElementById('currentPassword')?.value || '',
    newPassword: document.getElementById('newPassword')?.value || '',
    confirmPassword: document.getElementById('confirmNewPassword')?.value || ''
  };
  const res = await fetch('/api/users/me/password', {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to change password');
  alert(data.message || 'Password changed successfully');
  document.getElementById('currentPassword').value = '';
  document.getElementById('newPassword').value = '';
  document.getElementById('confirmNewPassword').value = '';
}
