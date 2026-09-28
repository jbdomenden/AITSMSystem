async function fetchJsonOrThrow(url, options = {}) {
  const response = await fetch(url, { headers: { ...authHeaders(), ...(options.headers || {}) }, ...options });
  const data = await response.json();
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
  await fetchJsonOrThrow('/settings/asset-ip-prefixes', { method: 'POST', body: JSON.stringify({ prefixes }) });
  alert('Asset detection prefixes saved.');
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
  try { await fetchJsonOrThrow('/api/ai/config', { method: 'POST', body: JSON.stringify({ model }) }); setAiMessage('Gemini model saved.'); }
  catch (error) { setAiMessage(error.message || 'Unable to save Gemini configuration.', true); }
  finally { setAiButtonsDisabled(false); }
}
async function testAiConnection() {
  setAiButtonsDisabled(true); setAiMessage('Testing Gemini connection...');
  try {
    await saveAiConfig();
    const data = await fetchJsonOrThrow('/api/ai/test', { method: 'POST', body: '{}' });
    setAiStatus('Connected', 'ok'); setAiMessage(data.message || 'Gemini connection successful.');
  } catch (error) { setAiStatus('Unavailable', 'fail'); setAiMessage(error.message || 'Unable to connect to Gemini.', true); }
  finally { setAiButtonsDisabled(false); }
}

document.addEventListener('DOMContentLoaded', async () => {
  document.getElementById('aiTestConnectionBtn')?.addEventListener('click', testAiConnection);
  document.getElementById('aiSaveConfigBtn')?.addEventListener('click', saveAiConfig);
  try { await Promise.all([loadSlaPolicies(), loadAssetDetectionPrefixes(), loadAiConfig()]); }
  catch (error) { alert(error.message || 'Unable to load settings data'); }
});
