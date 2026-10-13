async function acceptInvitation(event) {
  event?.preventDefault();
  const invitationToken = new URLSearchParams(location.search).get('invite') || '';
  if (!invitationToken) return;
  const submit = document.getElementById('acceptInvitationButton');
  const body = {
    token: invitationToken,
    fullName: document.getElementById('fullName').value.trim(),
    department: document.getElementById('department').value.trim(),
    password: document.getElementById('password').value,
    confirmPassword: document.getElementById('confirmPassword').value,
    eulaAccepted: document.getElementById('eula').checked
  };
  if (!body.eulaAccepted) return alert('Please accept the EULA to continue registration.');

  submit.disabled = true;
  submit.textContent = 'Accepting…';
  try {
    const res = await fetch('/api/auth/invitations/accept', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const data = await res.json();
    if (!res.ok) return alert(data.error || 'Invitation could not be accepted.');

    if (!data.user) return alert('Invitation could not be accepted.');
    if (data.mfaEnrollmentRequired) {
      alert('Your administrator account is ready. Sign in to complete mandatory MFA setup.');
      location.href = '/login';
      return;
    }
    window.currentUser = data.user;
    redirectForRole(data.user.role);
  } finally {
    submit.disabled = false;
    submit.textContent = 'Accept Invitation';
  }
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


document.addEventListener('DOMContentLoaded', async () => {
  const state = document.getElementById('invitationState');
  const form = document.getElementById('invitationAcceptanceForm');
  form?.addEventListener('submit', acceptInvitation);
  const token = new URLSearchParams(location.search).get('invite') || '';
  if (!token) {
    state.innerHTML = `A valid invitation link is required. Return to the <a href='/login' class='text-primary'>login page</a>.`;
    return;
  }
  try {
    const response = await fetch(`/api/auth/invitations/inspect?token=${encodeURIComponent(token)}`);
    if (!response.ok) {
      state.innerHTML = `This invitation is invalid or expired. Ask your administrator for a new invitation or return to the <a href='/login' class='text-primary'>login page</a>.`;
      return;
    }
    const invitation = await response.json();
    document.getElementById('email').value = invitation.email || '';
    state.hidden = true;
    form.hidden = false;
  } catch (_) {
    state.textContent = 'The invitation could not be verified. Please try again later.';
  }
});
