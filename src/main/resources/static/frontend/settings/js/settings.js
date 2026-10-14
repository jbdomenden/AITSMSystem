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
  document.getElementById('slaRows').innerHTML = policies.map(policy => `<tr>
    <td>${aitsmEscapeHtml(policy.priority)}</td>
    <td><input id='slaResponse-${policy.id}' type='number' min='1' max='43200' value='${Number(policy.responseTime)}' aria-label='${aitsmEscapeHtml(policy.priority)} response minutes'></td>
    <td><input id='slaResolution-${policy.id}' type='number' min='1' max='43200' value='${Number(policy.resolutionTime)}' aria-label='${aitsmEscapeHtml(policy.priority)} resolution minutes'></td>
    <td><button class='btn btn-ghost' type='button' onclick='saveSlaPolicy(${policy.id}, ${JSON.stringify(policy.priority)})'>Save</button></td>
  </tr>`).join('');
}
async function saveSlaPolicy(id, priority) {
  const message = document.getElementById('slaPolicyMessage');
  const responseTime = Number(document.getElementById(`slaResponse-${id}`)?.value);
  const resolutionTime = Number(document.getElementById(`slaResolution-${id}`)?.value);
  if (!Number.isInteger(responseTime) || !Number.isInteger(resolutionTime) || responseTime < 1 || resolutionTime < 1) {
    message.textContent = 'Enter whole-minute targets greater than zero.';
    message.className = 'small text-danger';
    return;
  }
  if (responseTime > resolutionTime) {
    message.textContent = 'Response time cannot exceed resolution time.';
    message.className = 'small text-danger';
    return;
  }
  message.textContent = `Saving ${priority} SLA…`;
  message.className = 'small';
  try {
    await fetchJsonOrThrow(`/api/sla/${encodeURIComponent(priority)}`, {
      method: 'PUT', body: JSON.stringify({ responseTime, resolutionTime })
    }, true);
    message.textContent = `${priority} SLA saved.`;
    message.className = 'small text-success';
    await loadSlaPolicies();
  } catch (error) {
    message.textContent = error.message || 'Unable to save SLA policy.';
    message.className = 'small text-danger';
  }
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

let collectorRecords = new Map();

async function createAndDownloadCollector(packageRequest, status) {
  const enrollment = await fetchJsonOrThrow('/api/monitoring/collector-packages', { method: 'POST', body: JSON.stringify(packageRequest) });
  const response = await fetch('/api/monitoring/collector-installer/windows', {
    method: 'POST', headers: authHeaders(), body: JSON.stringify({ enrollmentCode: enrollment.enrollmentCode })
  });
  if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.error || 'Installer download failed'); }
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a'); link.href = url; link.download = collectorFilename(response); document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
  const target = enrollment.baseUrl || window.location.origin;
  status.textContent = `Installer downloaded for ${String(enrollment.environment || 'production').toUpperCase()} (${target}, ${enrollment.scope} scope). Run it within 10 minutes.`;
  status.className = 'small text-success';
}

async function downloadCollectorInstaller() {
  const button = document.getElementById('downloadCollectorBtn');
  const status = document.getElementById('collectorDownloadStatus');
  button.disabled = true; status.textContent = 'Creating a secure one-time installer…'; status.className = 'small';
  try {
    await createAndDownloadCollector({
      name: document.getElementById('collectorName').value.trim(),
      siteId: document.getElementById('collectorSite').value.trim(),
      scope: document.getElementById('collectorScope').value
    }, status);
  } catch (error) { status.textContent = error.message || 'Unable to create installer.'; status.className = 'small text-danger'; }
  finally { button.disabled = false; }
}

async function repairCollector(id) {
  const item = collectorRecords.get(id); if (!item) return;
  const status = document.getElementById('collectorDownloadStatus');
  status.textContent = `Creating a repair installer for ${item.name}…`; status.className = 'small';
  try {
    await createAndDownloadCollector({ name: item.name, siteId: item.siteId, scope: item.scope }, status);
  } catch (error) { status.textContent = error.message || 'Unable to create repair installer.'; status.className = 'small text-danger'; }
}

async function downloadCollectorDiagnostics(id) {
  const response = await fetch(`/api/monitoring/collectors/${encodeURIComponent(id)}/diagnostics`, { headers: authHeaders() });
  if (!response.ok) { const data = await response.json().catch(() => ({})); return showAppAlert(data.error || 'Unable to download diagnostics'); }
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a'); link.href = url; link.download = `AITSM-collector-${id}-diagnostics.txt`; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
}

async function loadCollectors() {
  const rows = document.getElementById('collectorRows');
  const collectors = await fetchJsonOrThrow('/api/monitoring/collectors');
  collectorRecords = new Map(collectors.map(item => [item.collectorId, item]));
  rows.innerHTML = collectors.length ? collectors.map(item => `<tr><td>${aitsmEscapeHtml(item.name)}<div class='small'>${aitsmEscapeHtml(item.statusReason || '')}</div><div class='small'>Agent ${aitsmEscapeHtml(item.agentVersion || 'legacy')} · Failures ${Number(item.consecutiveFailures || 0)}</div>${item.lastReportedError ? `<div class='small text-danger'>Last error: ${aitsmEscapeHtml(item.lastReportedError)}</div>` : ''}</td><td>${aitsmEscapeHtml(item.scope)}</td><td><span class='badge ${item.state === 'ONLINE' ? 'resolved' : 'warning'}'>${aitsmEscapeHtml(item.state)}</span></td><td>${aitsmEscapeHtml(item.lastSeenAt ? new Date(item.lastSeenAt).toLocaleString() : 'Never')}</td><td><div class='inline-actions'><button class='btn btn-ghost' type='button' onclick='downloadCollectorDiagnostics("${item.collectorId}")'>Diagnostics</button>${item.state === 'REVOKED' ? '' : `<button class='btn btn-ghost' type='button' onclick='repairCollector("${item.collectorId}")'>Repair</button><button class='btn btn-ghost' type='button' onclick='revokeCollector("${item.collectorId}")'>Revoke</button>`}</div></td></tr>`).join('') : `<tr><td colspan='5'>No collectors enrolled.</td></tr>`;
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
