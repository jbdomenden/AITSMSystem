const defaultAvatar = '/profile/assets/default-avatar.png';
const maxUploadBytes = 5 * 1024 * 1024;

function setPhotoStatus(message, isError = false) {
  const node = document.getElementById('profilePhotoStatus');
  if (!node) return;
  node.textContent = message || '';
  node.className = `small profile-photo-status ${isError ? 'text-danger' : ''}`;
}

async function loadProfile() {
  const res = await fetch('/api/users/me', { headers: authHeaders() });
  const user = await res.json();
  if (!res.ok) throw new Error(user.error || 'Unable to load your profile.');
  const assignText = (id, value) => {
    const element = document.getElementById(id);
    if (element) element.textContent = value || '—';
  };
  assignText('profileTitle', user.fullName);
  assignText('profileNameDetail', user.fullName);
  assignText('profileEmail', user.email);
  assignText('profileEmailDetail', user.email);
  assignText('profileDepartment', user.department);
  const image = document.getElementById('profilePhoto');
  image.src = user.profilePhotoUrl || defaultAvatar;
  image.onerror = () => { image.src = defaultAvatar; };
  const pendingRes = await fetch('/api/profile-photo-requests/mine', { headers: authHeaders() });
  if (pendingRes.ok) {
    const pending = await pendingRes.json();
    if (pending?.id) {
      setPhotoStatus('Photo submitted — waiting for admin approval.');
      document.getElementById('changeProfilePhoto').disabled = true;
    }
  }
}

function openSuccessModal() { document.getElementById('profilePhotoSuccessModal')?.classList.replace('hidden', 'show'); }
function closeSuccessModal() { document.getElementById('profilePhotoSuccessModal')?.classList.replace('show', 'hidden'); }

async function submitProfilePhoto(file) {
  if (!file) return;
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
  try { await loadProfile(); } catch (error) { setPhotoStatus(error.message, true); }
});
