const defaultAvatar = '/profile/assets/default-avatar.png';
const maxUploadBytes = 5 * 1024 * 1024;

function viewedProfileId() {
  const raw = new URLSearchParams(location.search).get('id');
  if (raw === null) return 0;
  const id = Number(raw);
  return Number.isInteger(id) && id > 0 ? id : -1;
}

function currentUserId() {
  return Number(localStorage.getItem('userId') || 0);
}

function isOwnProfile() {
  const viewedId = viewedProfileId();
  return viewedId === 0 || viewedId === currentUserId();
}

function setPhotoStatus(message, isError = false) {
  const node = document.getElementById('profilePhotoStatus');
  if (!node) return;
  node.textContent = message || '';
  node.className = `small profile-photo-status ${isError ? 'text-danger' : ''}`;
}

function assignText(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value || '—';
}

function renderProfile(user, { own = true } = {}) {
  document.title = own ? 'Profile | AITSM' : `${user.fullName || 'Profile'} | AITSM`;
  assignText('profileTitle', user.fullName);
  assignText('profileNameDetail', user.fullName);
  assignText('profileEmail', user.email);
  assignText('profileEmailDetail', user.email);
  assignText('profileDepartment', user.department);
  const image = document.getElementById('profilePhoto');
  image.src = user.profilePhotoUrl || defaultAvatar;
  image.alt = own ? 'Your profile photo' : `${user.fullName || 'Colleague'} profile photo`;
  image.onerror = () => { image.src = defaultAvatar; };
  const subtitle = document.getElementById('profileInfoSubtitle');
  if (subtitle) subtitle.textContent = own ? 'Your account details in AITSM.' : 'Account details in AITSM.';
  const camera = document.getElementById('changeProfilePhoto');
  if (camera) camera.hidden = !own;
  const approval = document.getElementById('photoApprovalCard');
  if (approval) approval.hidden = !own;
  document.querySelector('.profile-details-grid')?.classList.toggle('colleague-view', !own);
  const roleFact = document.getElementById('profileRoleFact');
  if (roleFact) {
    roleFact.hidden = own || !user.role;
    assignText('profileRole', String(user.role || '').replaceAll('_', ' '));
  }
}

function setProfileAction(html) {
  const actions = document.getElementById('profileActions');
  if (!actions) return;
  actions.innerHTML = html || '';
}

function renderColleagueAction(result) {
  const colleague = result.colleague;
  const back = '<a class="btn btn-ghost" href="/colleagues.html">Back to colleagues</a>';
  if (result.relationship === 'ACCEPTED') {
    setProfileAction(`${back}<button class="btn btn-primary" type="button" id="profileMessageBtn">Open chat</button>`);
    document.getElementById('profileMessageBtn')?.addEventListener('click', () => {
      openAitsmChat(colleague.id);
    });
    return;
  }
  if (result.relationship === 'PENDING') {
    setProfileAction(`${back}<span class="small relationship-note">${result.requestedByCurrentUser ? 'Request sent' : 'Incoming request'}</span>`);
    return;
  }
  setProfileAction(`${back}<button class="btn btn-primary" type="button" id="profileAddBtn">Add colleague</button>`);
  document.getElementById('profileAddBtn')?.addEventListener('click', () => addColleague(colleague.id));
}

async function fetchColleagueProfile(id) {
  const res = await fetch(`/api/colleagues/${id}/profile`, { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || data.message || 'Unable to load this profile.');
  return data;
}

async function addColleague(id) {
  const button = document.getElementById('profileAddBtn');
  if (button) button.disabled = true;
  try {
    const res = await fetch(`/api/colleagues/${id}/request`, { method: 'POST', headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || data.message || 'Unable to add this colleague.');
    const profile = await fetchColleagueProfile(id);
    renderColleagueAction(profile);
  } catch (error) {
    alert(error.message);
    if (button) button.disabled = false;
  }
}

async function loadOwnProfile() {
  const res = await fetch('/api/users/me', { headers: authHeaders() });
  const user = await res.json();
  if (!res.ok) throw new Error(user.error || 'Unable to load your profile.');
  renderProfile(user, { own: true });
  setProfileAction('');
  const pendingRes = await fetch('/api/profile-photo-requests/mine', { headers: authHeaders() });
  if (pendingRes.ok) {
    const pending = await pendingRes.json();
    if (pending?.id) {
      setPhotoStatus('Photo submitted — waiting for admin approval.');
      document.getElementById('changeProfilePhoto').disabled = true;
    }
  }
}

async function loadColleagueProfile() {
  const profile = await fetchColleagueProfile(viewedProfileId());
  renderProfile(profile.colleague, { own: false });
  renderColleagueAction(profile);
}

async function loadProfile() {
  if (viewedProfileId() < 0) throw new Error('The requested colleague profile is unavailable.');
  if (isOwnProfile()) return loadOwnProfile();
  return loadColleagueProfile();
}

function renderProfileError(message) {
  assignText('profileTitle', 'Profile unavailable');
  assignText('profileEmail', 'The requested colleague could not be displayed.');
  document.getElementById('changeProfilePhoto')?.setAttribute('hidden', '');
  document.getElementById('photoApprovalCard')?.setAttribute('hidden', '');
  setProfileAction('<a class="btn btn-ghost" href="/colleagues.html">Back to colleagues</a>');
  setPhotoStatus(message || 'This profile is unavailable.', true);
}

function openSuccessModal() { document.getElementById('profilePhotoSuccessModal')?.classList.replace('hidden', 'show'); }
function closeSuccessModal() { document.getElementById('profilePhotoSuccessModal')?.classList.replace('show', 'hidden'); }

async function submitProfilePhoto(file) {
  if (!file || !isOwnProfile()) return;
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return setPhotoStatus('Choose a JPG, PNG, or WEBP image.', true);
  if (file.size > maxUploadBytes) return setPhotoStatus('Image must be 5 MB or smaller.', true);
  const button = document.getElementById('changeProfilePhoto');
  button.disabled = true;
  setPhotoStatus('Submitting photo for approval...');
  try {
    const body = new FormData();
    body.append('photo', file);
    const res = await fetch('/api/profile-photo-requests', { method: 'POST', headers: { 'X-User-Id': localStorage.getItem('userId') || '', 'X-User-Role': currentRole() }, body });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || data.message || 'Unable to submit your photo.');
    setPhotoStatus('Photo submitted — waiting for admin approval.');
    openSuccessModal();
  } catch (error) {
    setPhotoStatus(error.message || 'Upload failed. Please try again.', true);
  } finally {
    button.disabled = false;
    document.getElementById('profilePhotoInput').value = '';
  }
}

document.addEventListener('DOMContentLoaded', async () => {
  document.getElementById('changeProfilePhoto')?.addEventListener('click', () => document.getElementById('profilePhotoInput')?.click());
  document.getElementById('profilePhotoInput')?.addEventListener('change', (event) => submitProfilePhoto(event.target.files?.[0]));
  document.getElementById('closeProfilePhotoSuccess')?.addEventListener('click', closeSuccessModal);
  document.getElementById('profilePhotoSuccessModal')?.addEventListener('click', (event) => { if (event.target.id === 'profilePhotoSuccessModal') closeSuccessModal(); });
  try { await loadProfile(); } catch (error) { renderProfileError(error.message); }
});
