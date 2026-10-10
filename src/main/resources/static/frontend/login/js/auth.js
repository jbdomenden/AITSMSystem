function saveSession(data) {
  window.currentUser = data.user;
}

let pendingMfaAuth = null;

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
  const credentials = { email: email.value.trim(), password: password.value };
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || data.message || 'Login failed');

  if (data.mfaRequired) {
    const code = window.prompt('Enter your 6-digit authenticator code (or a recovery code):') || '';
    if (!code) return;
    const second = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...credentials, ...(code.includes('-') || code.length > 6 ? { recoveryCode: code } : { mfaCode: code }) }) });
    const completed = await second.json();
    if (!second.ok || completed.mfaRequired) return alert(completed.error || 'MFA verification failed');
    saveSession(completed); redirectForRole(completed.user.role); return;
  }
  if (data.mfaEnrollmentRequired) {
    const enrollmentResponse = await fetch('/api/auth/mfa/enroll', { method: 'POST', headers: authHeaders() });
    const enrollment = await enrollmentResponse.json();
    if (!enrollmentResponse.ok) return alert(enrollment.error || enrollment.message || 'MFA enrollment failed');
    pendingMfaAuth = data;
    showMfaEnrollment(enrollment);
    return;
  }

  saveSession(data);
  redirectForRole(data.user.role);
}

function setRecoveryMessage(elementId, message = '', tone = 'danger') {
  const target = document.getElementById(elementId);
  if (!target) return;
  target.textContent = message;
  target.classList.toggle('hidden', !message);
  target.classList.toggle('text-danger', Boolean(message) && tone === 'danger');
  target.classList.toggle('text-success', Boolean(message) && tone === 'success');
  target.setAttribute('role', tone === 'danger' ? 'alert' : 'status');
}

function setRecoverySubmitState(buttonId, busy, busyLabel, idleLabel) {
  const button = document.getElementById(buttonId);
  if (!button) return;
  button.disabled = busy;
  button.textContent = busy ? busyLabel : idleLabel;
}

function openForgotPasswordModal() {
  const modal = document.getElementById('forgotPasswordModal');
  const loginEmail = document.getElementById('email')?.value.trim() || '';
  const recoveryEmail = document.getElementById('forgotPasswordEmail');
  if (recoveryEmail && loginEmail) recoveryEmail.value = loginEmail;
  setRecoveryMessage('forgotPasswordMessage');
  modal?.classList.remove('hidden');
  recoveryEmail?.focus();
}

function closeForgotPasswordModal() {
  document.getElementById('forgotPasswordModal')?.classList.add('hidden');
  setRecoveryMessage('forgotPasswordMessage');
}

async function submitForgotPassword(event) {
  event?.preventDefault();
  const email = document.getElementById('forgotPasswordEmail')?.value.trim() || '';
  if (!email) return setRecoveryMessage('forgotPasswordMessage', 'Enter your account email.');

  setRecoverySubmitState('forgotPasswordSubmit', true, 'Sending…', 'Send reset link');
  setRecoveryMessage('forgotPasswordMessage');
  try {
    const response = await fetch('/api/auth/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      setRecoveryMessage('forgotPasswordMessage', data.error || data.message || 'Unable to request a reset link.');
      return;
    }
    setRecoveryMessage('forgotPasswordMessage', data.message || 'If the account is eligible, a reset link will be sent.', 'success');
  } catch {
    setRecoveryMessage('forgotPasswordMessage', 'Unable to reach the server. Check your connection and try again.');
  } finally {
    setRecoverySubmitState('forgotPasswordSubmit', false, 'Sending…', 'Send reset link');
  }
}

function openResetPasswordModal() {
  setRecoveryMessage('resetPasswordMessage');
  document.getElementById('resetPasswordModal')?.classList.remove('hidden');
  document.getElementById('resetNewPassword')?.focus();
}

function closeResetPasswordModal() {
  document.getElementById('resetPasswordModal')?.classList.add('hidden');
  history.replaceState({}, document.title, location.pathname);
  ['resetNewPassword', 'resetConfirmPassword'].forEach(id => {
    const input = document.getElementById(id);
    if (input) input.value = '';
  });
  setRecoveryMessage('resetPasswordMessage');
}

async function submitPasswordReset(event) {
  event?.preventDefault();
  const token = new URLSearchParams(location.search).get('reset') || '';
  const newPassword = document.getElementById('resetNewPassword')?.value || '';
  const confirmPassword = document.getElementById('resetConfirmPassword')?.value || '';
  if (!token) return setRecoveryMessage('resetPasswordMessage', 'This reset link is invalid or incomplete. Request a new link.');
  if (newPassword.length < 12) return setRecoveryMessage('resetPasswordMessage', 'New password must be at least 12 characters.');
  if (newPassword !== confirmPassword) return setRecoveryMessage('resetPasswordMessage', 'New password and confirmation do not match.');

  setRecoverySubmitState('resetPasswordSubmit', true, 'Updating…', 'Update password');
  setRecoveryMessage('resetPasswordMessage');
  try {
    const response = await fetch('/api/auth/reset-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, newPassword, confirmPassword })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      setRecoveryMessage('resetPasswordMessage', data.error || data.message || 'Unable to reset the password. Request a new link.');
      return;
    }
    setRecoveryMessage('resetPasswordMessage', data.message || 'Password reset completed. Sign in with your new password.', 'success');
    history.replaceState({}, document.title, location.pathname);
    document.getElementById('resetPasswordSubmit')?.classList.add('hidden');
    document.getElementById('resetPasswordCancel').textContent = 'Return to sign in';
  } catch {
    setRecoveryMessage('resetPasswordMessage', 'Unable to reach the server. Check your connection and try again.');
  } finally {
    setRecoverySubmitState('resetPasswordSubmit', false, 'Updating…', 'Update password');
  }
}

function showMfaEnrollment(enrollment) {
  const modal = document.getElementById('mfaEnrollmentModal');
  document.getElementById('mfaQrCode').src = enrollment.qrCodeDataUrl;
  document.getElementById('mfaManualKey').textContent = enrollment.manualKey;
  document.getElementById('mfaEnrollmentCode').value = '';
  document.getElementById('mfaEnrollmentError').textContent = '';
  document.getElementById('mfaSetupStep').classList.remove('hidden');
  document.getElementById('mfaRecoveryStep').classList.add('hidden');
  modal.classList.remove('hidden');
  document.getElementById('mfaEnrollmentCode').focus();
}

async function confirmMfaEnrollment() {
  const codeInput = document.getElementById('mfaEnrollmentCode');
  const error = document.getElementById('mfaEnrollmentError');
  const button = document.getElementById('mfaConfirmButton');
  const code = codeInput.value.trim();
  if (!/^\d{6}$/.test(code)) {
    error.textContent = 'Enter the current six-digit code from your authenticator app.';
    codeInput.focus();
    return;
  }
  error.textContent = '';
  button.disabled = true;
  try {
    const confirmation = await fetch('/api/auth/mfa/confirm', {
      method: 'POST', headers: authHeaders(), body: JSON.stringify({ code })
    });
    const recovery = await confirmation.json();
    if (!confirmation.ok) throw new Error(recovery.error || recovery.message || 'MFA confirmation failed');
    document.getElementById('mfaRecoveryCodes').textContent = recovery.recoveryCodes.join('\n');
    document.getElementById('mfaSetupStep').classList.add('hidden');
    document.getElementById('mfaRecoveryStep').classList.remove('hidden');
  } catch (failure) {
    error.textContent = failure.message || 'MFA confirmation failed. Try the newest code.';
    codeInput.select();
  } finally {
    button.disabled = false;
  }
}

async function cancelMfaEnrollment() {
  await fetch('/api/auth/logout', { method: 'POST', headers: authHeaders() }).catch(() => {});
  pendingMfaAuth = null;
  window.currentUser = null;
  location.href = '/login.html';
}

function finishMfaEnrollment() {
  if (!pendingMfaAuth?.user) return;
  saveSession(pendingMfaAuth);
  redirectForRole(pendingMfaAuth.user.role);
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

document.addEventListener('DOMContentLoaded', () => {
  setPasswordVisibility('password', 'loginPasswordToggle');
  document.getElementById('forgotPasswordForm')?.addEventListener('submit', submitForgotPassword);
  document.getElementById('forgotPasswordCancel')?.addEventListener('click', closeForgotPasswordModal);
  document.getElementById('resetPasswordForm')?.addEventListener('submit', submitPasswordReset);
  document.getElementById('resetPasswordCancel')?.addEventListener('click', closeResetPasswordModal);
  document.getElementById('forgotPasswordModal')?.addEventListener('click', event => {
    if (event.target?.id === 'forgotPasswordModal') closeForgotPasswordModal();
  });
  document.getElementById('resetPasswordModal')?.addEventListener('click', event => {
    if (event.target?.id === 'resetPasswordModal') closeResetPasswordModal();
  });
  if (new URLSearchParams(location.search).has('reset')) openResetPasswordModal();
  document.getElementById('mfaEnrollmentCode')?.addEventListener('keydown', event => {
    if (event.key === 'Enter') confirmMfaEnrollment();
  });
});
