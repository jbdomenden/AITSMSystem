function saveSession(data) {
  localStorage.setItem('userId', data.user.id);
  localStorage.setItem('role', data.user.role);
}

function toggleVerificationStep(email) {
  const registration = document.getElementById('registrationStep');
  const verify = document.getElementById('verificationStep');
  const verifyEmail = document.getElementById('verifyEmail');
  if (!registration || !verify) return;
  registration.style.display = 'none';
  verify.style.display = 'block';
  if (verifyEmail) verifyEmail.value = email || '';
}

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

  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Registration failed');

  const hint = data.devVerificationCode ? `\nVerification code (dev mode): ${data.devVerificationCode}` : '';
  alert(`${data.message}${hint}`);
  toggleVerificationStep(data.email || body.email);
}

async function verifyEmailCode() {
  const email = verifyEmail.value.trim();
  const code = verificationCode.value.trim();
  const res = await fetch('/api/auth/verify-email', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Verification failed');

  saveSession(data);
  location.href = ['admin', 'superadmin'].includes(data.user.role) ? '/dashboard-admin.html' : '/dashboard-user.html';
}

async function resendVerificationCode() {
  const email = (document.getElementById('verifyEmail')?.value || '').trim();
  if (!email) return alert('Enter your email to resend a code.');
  const res = await fetch('/api/auth/resend-verification', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to resend code');

  alert(`Verification code resent.${data.devVerificationCode ? `\nCode (dev mode): ${data.devVerificationCode}` : ''}`);
}

async function login() {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: email.value.trim(), password: password.value })
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Login failed');

  saveSession(data);
  location.href = ['admin', 'superadmin'].includes(data.user.role) ? '/dashboard-admin.html' : '/dashboard-user.html';
}


function openEulaModal() {
  const modal = document.getElementById('eulaModal');
  if (!modal) return;
  modal.classList.remove('hidden');
}

function closeEulaModal() {
  const modal = document.getElementById('eulaModal');
  if (!modal) return;
  modal.classList.add('hidden');
}

function closeEulaModalOnOverlay(event) {
  if (event.target?.id === 'eulaModal') closeEulaModal();
}

function acceptEulaFromModal() {
  const checkbox = document.getElementById('eula');
  if (checkbox) checkbox.checked = true;
  closeEulaModal();
}
