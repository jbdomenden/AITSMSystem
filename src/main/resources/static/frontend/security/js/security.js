function securityEscape(value) {
  return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

async function securityJson(url, options = {}) {
  const response = await fetch(url, { ...options, headers: { ...authHeaders(), ...(options.headers || {}) } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || 'Security request failed');
  return data;
}

async function loadSessions() {
  const sessions = await securityJson('/api/auth/sessions');
  document.getElementById('sessions').innerHTML = sessions.map(s => `<div class="card"><strong>${s.current ? 'Current session' : 'Active session'}</strong><p class="small">${securityEscape(s.device || 'Unknown device')} · ${securityEscape(s.approximateIp || 'IP unavailable')}<br>Created ${securityEscape(s.createdAt)}; last activity ${securityEscape(s.lastSeenAt)}</p>${s.current ? '' : `<button class="btn btn-ghost" onclick="revokeSession('${encodeURIComponent(s.id)}')">Revoke</button>`}</div>`).join('') || '<p>No active sessions.</p>';
}

async function loadMfa() {
  const status = await securityJson('/api/auth/mfa/status');
  document.getElementById('mfaStatus').textContent = status.enabled ? 'MFA is enabled.' : (status.required ? 'MFA enrollment is required for this administrator account.' : 'MFA is not enabled.');
  const actions = [];
  if (!status.enabled) actions.push('<button class="btn btn-primary" type="button" onclick="beginMfaEnrollment()">Set up MFA</button>');
  if (status.enabled) actions.push('<button class="btn btn-ghost" type="button" onclick="regenerateRecoveryCodes()">Regenerate recovery codes</button>');
  if (status.enabled && !status.required) actions.push('<button class="btn btn-danger" type="button" onclick="disableMfa()">Disable MFA</button>');
  document.getElementById('mfaActions').innerHTML = actions.join('');
}

async function beginMfaEnrollment() {
  try {
    const enrollment = await securityJson('/api/auth/mfa/enroll', { method: 'POST' });
    document.getElementById('mfaEnrollment').innerHTML = `<div class="card"><p>Scan this QR code, or enter the manual key in your authenticator app.</p><img src="${enrollment.qrCodeDataUrl}" alt="TOTP enrollment QR code"><p><code>${securityEscape(enrollment.manualKey)}</code></p><div class="form-group"><label for="mfaConfirmCode">Six-digit code</label><input id="mfaConfirmCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6"></div><button class="btn btn-primary" onclick="confirmMfaEnrollment()">Confirm enrollment</button></div>`;
  } catch (error) { alert(error.message); }
}

async function confirmMfaEnrollment() {
  try {
    const recovery = await securityJson('/api/auth/mfa/confirm', { method: 'POST', body: JSON.stringify({ code: document.getElementById('mfaConfirmCode').value.trim() }) });
    showRecoveryCodes(recovery.recoveryCodes);
    document.getElementById('mfaEnrollment').innerHTML = '';
    await loadMfa();
  } catch (error) { alert(error.message); }
}

function showRecoveryCodes(codes) {
  document.getElementById('recoveryCodes').innerHTML = `<div class="card"><strong>Save these recovery codes now.</strong><p class="small">They are shown once. Store them offline; each code works only once.</p><pre>${codes.map(securityEscape).join('\n')}</pre></div>`;
}

async function regenerateRecoveryCodes() {
  if (!confirm('Invalidate all existing recovery codes and generate new ones?')) return;
  try {
    const token = await obtainReauthToken('recovery-codes');
    const recovery = await securityJson('/api/auth/mfa/recovery-codes', { method: 'POST', headers: { 'X-Reauth-Token': token } });
    consumeCachedReauth('recovery-codes');
    showRecoveryCodes(recovery.recoveryCodes);
  } catch (error) { alert(error.message); }
}

async function disableMfa() {
  if (!confirm('Disable MFA for this account?')) return;
  try {
    const token = await obtainReauthToken('mfa-change');
    await securityJson('/api/auth/mfa', { method: 'DELETE', headers: { 'X-Reauth-Token': token } });
    consumeCachedReauth('mfa-change');
    document.getElementById('recoveryCodes').innerHTML = '';
    await loadMfa();
  } catch (error) { alert(error.message); }
}

async function loadSecurityEvents() {
  const events = await securityJson('/api/auth/security-events');
  document.getElementById('securityEvents').innerHTML = events.map(event => `<div class="card"><strong>${securityEscape(event.action)}</strong><p class="small">${securityEscape(event.timestamp)} · ${securityEscape(event.result)} · ${securityEscape(event.entity)}</p></div>`).join('') || '<p class="small">No recent security activity.</p>';
}

async function revokeSession(id) { await securityJson(`/api/auth/sessions/${id}`, { method: 'DELETE' }); await loadSessions(); }
async function logoutEverywhere() { await securityJson('/api/auth/logout-all', { method: 'POST' }); location.href = '/login'; }
document.addEventListener('DOMContentLoaded', async () => {
  await window.aitsmReady;
  await Promise.all([loadSessions(), loadMfa(), loadSecurityEvents()]).catch(error => alert(error.message));
});
