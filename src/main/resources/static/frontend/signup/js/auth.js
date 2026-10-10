function saveSession(data) {
  window.currentUser = data.user;
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
  const invitationToken = new URLSearchParams(location.search).get('invite') || '';
  if (!invitationToken) return alert('A valid invitation link is required.');
  const body = {
    token: invitationToken,
    fullName: fullName.value.trim(),
    department: department.value.trim(),
    password: password.value,
    confirmPassword: confirmPassword.value,
    eulaAccepted: eula.checked
  };
  if (!body.eulaAccepted) return alert('Please accept the EULA to continue registration.');

  const res = await fetch('/api/auth/invitations/accept', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Registration failed');

  if (!data.user) return alert('Invitation could not be accepted.');
  if (data.mfaEnrollmentRequired) {
    alert('Your administrator account is ready. Sign in to complete mandatory MFA setup.');
    location.href = '/login.html';
    return;
  }
  saveSession(data);
  redirectForRole(data.user.role);
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

  if (data.mfaRequired || data.mfaEnrollmentRequired) {
    alert('Email verified. Sign in to complete administrator MFA.');
    location.href = '/login.html';
    return;
  }
  saveSession(data);
  redirectForRole(data.user.role);
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

  alert(`${data.message || 'Verification code resent.'}${data.devVerificationCode ? `\nCode (dev mode): ${data.devVerificationCode}` : ''}`);
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
  redirectForRole(data.user.role);
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


function setPasswordVisibility(inputId, toggleId) {
  const input = document.getElementById(inputId);
  const toggle = document.getElementById(toggleId);
  if (!input || !toggle) return;

  const showIcon = toggle.querySelector('.password-toggle-show');
  const hideIcon = toggle.querySelector('.password-toggle-hide');

  const sync = () => {
    const isVisible = input.type === 'text';
    toggle.setAttribute('aria-label', isVisible ? 'Hide password' : 'Show password');
    toggle.setAttribute('title', isVisible ? 'Hide password' : 'Show password');
    toggle.setAttribute('aria-pressed', String(isVisible));
    showIcon?.classList.toggle('hidden', isVisible);
    hideIcon?.classList.toggle('hidden', !isVisible);
  };

  toggle.addEventListener('click', () => {
    input.type = input.type === 'password' ? 'text' : 'password';
    sync();
    input.focus({ preventScroll: true });
    const end = input.value.length;
    input.setSelectionRange?.(end, end);
  });

  sync();
}

document.addEventListener('DOMContentLoaded', async () => {
  setPasswordVisibility('password', 'loginPasswordToggle');
  const token = new URLSearchParams(location.search).get('invite') || '';
  if (!token) return alert('A valid invitation link is required to register.');
  const response = await fetch(`/api/auth/invitations/inspect?token=${encodeURIComponent(token)}`);
  if (!response.ok) return alert('This invitation is invalid or expired.');
  const invitation = await response.json();
  email.value = invitation.email || '';
});
