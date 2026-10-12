async function fetchJsonOrThrow(url, options = {}, requiresReauth = false) {
  const reauthToken = requiresReauth ? await obtainReauthToken('security-config') : '';
  const response = await fetch(url, {
    ...options,
    headers: { ...authHeaders(), ...(reauthToken ? { 'X-Reauth-Token': reauthToken } : {}), ...(options.headers || {}) }
  });
  const data = await response.json().catch(() => ({}));
  if (requiresReauth) consumeCachedReauth('security-config');
  if (!response.ok) throw new Error(data.error || data.message || 'Request failed');
  return data;
}

function setAiMessage(message, error = false) {
  const node = document.getElementById('aiConfigMessage');
  if (node) { node.textContent = message; node.classList.toggle('text-danger', error); }
}
function setAiStatus(message, tone = 'neutral') {
  const node = document.getElementById('aiConnectionStatus');
  if (node) { node.textContent = message; node.className = `status-pill status-pill-${tone}`; }
}
function setAiButtonsDisabled(disabled) { ['aiTestConnectionBtn', 'aiSaveConfigBtn'].forEach(id => { const button = document.getElementById(id); if (button) button.disabled = disabled; }); }

async function loadSlaPolicies() {
  const policies = await fetchJsonOrThrow('/api/sla');
  document.getElementById('slaRows').innerHTML = policies.map(policy => `<tr><td>${policy.priority}</td><td>${policy.responseTime}</td><td>${policy.resolutionTime}</td></tr>`).join('');
}
async function loadAssetDetectionPrefixes() {
  const data = await fetchJsonOrThrow('/settings/asset-ip-prefixes');
  document.getElementById('assetIpPrefixes').value = (data.prefixes || []).join('\n');
}
async function saveAssetDetectionPrefixes() {
  const prefixes = document.getElementById('assetIpPrefixes').value.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
  await fetchJsonOrThrow('/settings/asset-ip-prefixes', { method: 'POST', body: JSON.stringify({ prefixes }) }, true);
  showAppAlert('Asset detection prefixes saved.');
}

async function loadAiConfig() {
  const data = await fetchJsonOrThrow('/api/ai/config');
  document.getElementById('aiModel').value = data.model || 'gemini-3.8-flash';
  setAiStatus('Not tested', 'neutral');
  setAiMessage('Gemini credentials are configured securely on the server.');
}
async function saveAiConfig() {
  const model = document.getElementById('aiModel').value.trim();
  if (!model) return setAiMessage('A Gemini model is required.', true);
  setAiButtonsDisabled(true); setAiMessage('Saving Gemini configuration...');
  try { await fetchJsonOrThrow('/api/ai/config', { method: 'POST', body: JSON.stringify({ model }) }, true); setAiMessage('Gemini model saved.'); }
  catch (error) { setAiMessage(error.message || 'Unable to save Gemini configuration.', true); }
  finally { setAiButtonsDisabled(false); }
}
async function testAiConnection() {
  setAiButtonsDisabled(true); setAiMessage('Testing Gemini connection...');
  try {
    const data = await fetchJsonOrThrow('/api/ai/test', { method: 'POST', body: '{}' }, true);
    setAiStatus('Connected', 'ok'); setAiMessage(data.message || 'Gemini connection successful.');
  } catch (error) { setAiStatus('Unavailable', 'fail'); setAiMessage(error.message || 'Unable to connect to Gemini.', true); }
  finally { setAiButtonsDisabled(false); }
}

function collectorFilename(response) {
  const disposition = response.headers.get('content-disposition') || '';
  return disposition.match(/filename="([^"]+)"/i)?.[1] || 'AITSMCollectorSetup.exe';
}

async function downloadCollectorInstaller() {
  const button = document.getElementById('downloadCollectorBtn');
  const status = document.getElementById('collectorDownloadStatus');
  button.disabled = true; status.textContent = 'Creating a secure one-time installer…'; status.className = 'small';
  try {
    const enrollment = await fetchJsonOrThrow('/api/monitoring/collector-packages', {
      method: 'POST', body: JSON.stringify({
        name: document.getElementById('collectorName').value.trim(),
        siteId: document.getElementById('collectorSite').value.trim(),
        scope: document.getElementById('collectorScope').value
      })
    });
    const response = await fetch('/api/monitoring/collector-installer/windows', {
      method: 'POST', headers: authHeaders(), body: JSON.stringify({ enrollmentCode: enrollment.enrollmentCode })
    });
    if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.error || 'Installer download failed'); }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a'); link.href = url; link.download = collectorFilename(response); document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
    status.textContent = `Installer downloaded. Run it within 10 minutes (${enrollment.scope} scope).`; status.className = 'small text-success';
  } catch (error) { status.textContent = error.message || 'Unable to create installer.'; status.className = 'small text-danger'; }
  finally { button.disabled = false; }
}

async function loadCollectors() {
  const rows = document.getElementById('collectorRows');
  const collectors = await fetchJsonOrThrow('/api/monitoring/collectors');
  rows.innerHTML = collectors.length ? collectors.map(item => `<tr><td>${aitsmEscapeHtml(item.name)}</td><td>${aitsmEscapeHtml(item.scope)}</td><td><span class='badge ${item.state === 'ONLINE' ? 'resolved' : 'warning'}'>${aitsmEscapeHtml(item.state)}</span></td><td>${aitsmEscapeHtml(item.lastSeenAt ? new Date(item.lastSeenAt).toLocaleString() : 'Never')}</td><td>${item.state === 'REVOKED' ? '-' : `<button class='btn btn-ghost' type='button' onclick='revokeCollector("${item.collectorId}")'>Revoke</button>`}</td></tr>`).join('') : `<tr><td colspan='5'>No collectors enrolled.</td></tr>`;
}

async function revokeCollector(id) {
  if (!confirm('Revoke this collector? It will stop accepting reports immediately.')) return;
  await fetchJsonOrThrow(`/api/monitoring/collectors/${encodeURIComponent(id)}`, { method: 'DELETE' });
  await loadCollectors();
}

document.addEventListener('DOMContentLoaded', async () => {
  document.getElementById('aiTestConnectionBtn')?.addEventListener('click', testAiConnection);
  document.getElementById('aiSaveConfigBtn')?.addEventListener('click', saveAiConfig);
  document.getElementById('downloadCollectorBtn')?.addEventListener('click', downloadCollectorInstaller);
  try { await Promise.all([loadSlaPolicies(), loadAssetDetectionPrefixes(), loadAiConfig(), loadCollectors()]); }
  catch (error) { showAppAlert(error.message || 'Unable to load settings data'); }
});
