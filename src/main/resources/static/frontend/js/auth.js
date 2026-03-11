async function signup() {
  const body = {
    fullName: fullName.value.trim(),
    email: email.value.trim(),
    company: company.value.trim(),
    department: department.value.trim(),
    password: password.value,
    confirmPassword: confirmPassword.value,
    eulaAccepted: eula.checked
  };
  if (!body.eulaAccepted) return alert('Please accept the EULA to continue registration.');
  const res = await fetch('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Registration failed');
  localStorage.setItem('userId', data.user.id);
  localStorage.setItem('role', data.user.role);
  location.href = data.user.role === 'admin' ? '/dashboard-admin.html' : '/dashboard-user.html';
}

async function login() {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: email.value.trim(), password: password.value })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Login failed');
  localStorage.setItem('userId', data.user.id);
  localStorage.setItem('role', data.user.role);
  location.href = data.user.role === 'admin' ? '/dashboard-admin.html' : '/dashboard-user.html';
}
