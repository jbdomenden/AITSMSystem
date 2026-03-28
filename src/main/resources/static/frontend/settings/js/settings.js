async function fetchJsonOrThrow(url, options = {}) {
  const res = await fetch(url, { headers: authHeaders(), ...options });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || data.message || `Failed request: ${url}`);
  return data;
}

async function loadProfileSettings() {
  const user = await fetchJsonOrThrow('/api/users/me');
  document.getElementById('settingsFullName').value = user.fullName || '';
  document.getElementById('settingsEmail').value = user.email || '';
  document.getElementById('settingsCompany').value = user.company || '';
  document.getElementById('settingsDepartment').value = user.department || '';
}

async function saveProfileSettings() {
  const body = {
    fullName: (document.getElementById('settingsFullName')?.value || '').trim(),
    company: (document.getElementById('settingsCompany')?.value || '').trim(),
    department: (document.getElementById('settingsDepartment')?.value || '').trim()
  };

  const res = await fetch('/api/users/me', {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to save profile');
  alert('Profile updated successfully.');
}

async function loadSlaPolicies() {
  const rows = document.getElementById('slaRows');
  if (!rows) return;
  showTableSkeleton(rows, { rowCount: 4, columnCount: 3 });
  try {
    const policies = await fetchJsonOrThrow('/api/sla');
    clearTableSkeleton(rows);
    if (!policies.length) {
      renderTableEmptyState(rows, 3, 'No SLA policies found.');
      return;
    }
    rows.innerHTML = policies.map((p) => `<tr><td>${p.priority}</td><td>${p.responseTime}</td><td>${p.resolutionTime}</td></tr>`).join('');
  } catch (error) {
    renderTableErrorState(rows, 3, error.message || 'Unable to load SLA policies');
  } finally {
    clearTableSkeleton(rows);
  }
}

async function loadAssetDetectionPrefixes() {
  const area = document.getElementById('assetIpPrefixes');
  if (!area) return;
  const data = await fetchJsonOrThrow('/settings/asset-ip-prefixes');
  area.value = Array.isArray(data.prefixes) ? data.prefixes.join('\n') : '';
}

function parsePrefixTextarea(value) {
  return (value || '')
    .split(/\r?\n|,/)
    .map((v) => v.trim())
    .filter(Boolean);
}

async function saveAssetDetectionPrefixes() {
  const area = document.getElementById('assetIpPrefixes');
  if (!area) return;
  const prefixes = parsePrefixTextarea(area.value);
  if (!prefixes.length) {
    alert('Please add at least one IP prefix.');
    return;
  }

  const data = await fetchJsonOrThrow('/settings/asset-ip-prefixes', {
    method: 'POST',
    body: JSON.stringify({ prefixes })
  });
  area.value = Array.isArray(data.prefixes) ? data.prefixes.join('\n') : '';
  alert(data.message || 'Asset detection prefixes saved.');
}

function setAiMessage(text, isError = false) {
  const host = document.getElementById('aiConfigMessage');
  if (!host) return;
  host.textContent = text || '';
  host.classList.toggle('text-danger', Boolean(isError));
}

function setAiStatus(label, variant) {
  const el = document.getElementById('aiConnectionStatus');
  if (!el) return;
  el.textContent = label;
  el.className = `status-pill status-pill-${variant}`;
}

function setAiButtonsDisabled(disabled) {
  ['aiRefreshModelsBtn', 'aiTestConnectionBtn', 'aiSaveConfigBtn'].forEach((id) => {
    const btn = document.getElementById(id);
    if (btn) btn.disabled = disabled;
  });
}

function validUrl(url) {
  try {
    const parsed = new URL(url);
    return ['http:', 'https:'].includes(parsed.protocol);
  } catch {
    return false;
  }
}

function currentAiFormState() {
  return {
    baseUrl: (document.getElementById('aiBaseUrl')?.value || '').trim(),
    model: (document.getElementById('aiModelSelect')?.value || '').trim()
  };
}

function renderModelOptions(models = [], selectedModel = '') {
  const select = document.getElementById('aiModelSelect');
  const hint = document.getElementById('aiModelHint');
  if (!select) return;

  if (!models.length) {
    select.innerHTML = "<option value=''>No models found</option>";
    select.value = '';
    if (hint) hint.textContent = 'No local models found. Run `ollama pull <model>` first.';
    return;
  }

  select.innerHTML = models.map((name) => `<option value='${name}'>${name}</option>`).join('');
  select.value = models.includes(selectedModel) ? selectedModel : models[0];
  if (hint) hint.textContent = `${models.length} model(s) detected from local Ollama.`;
}

async function loadAiConfig() {
  const data = await fetchJsonOrThrow('/api/ai/config');
  const input = document.getElementById('aiBaseUrl');
  if (input) input.value = data.baseUrl || 'http://localhost:11434';
  setAiStatus('Unknown', 'neutral');
  await refreshModels();
}

async function refreshModels() {
  const { baseUrl } = currentAiFormState();
  if (!baseUrl || !validUrl(baseUrl)) {
    setAiMessage('Base URL must be a valid http(s) URL before refreshing models.', true);
    return;
  }

  setAiButtonsDisabled(true);
  setAiMessage('Refreshing model list...');
  try {
    const saveRes = await fetch('/api/ai/config', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ baseUrl, model: (document.getElementById('aiModelSelect')?.value || 'llama3.1:8b') })
    });
    if (!saveRes.ok) {
      const err = await saveRes.json();
      throw new Error(err.error || err.message || 'Unable to apply base URL');
    }

    const data = await fetchJsonOrThrow('/api/ai/models');
    renderModelOptions(data.models || [], data.currentModel || '');
    setAiMessage('Model list refreshed successfully.');
  } catch (error) {
    renderModelOptions([], '');
    setAiMessage(error.message || 'Failed to refresh models.', true);
    setAiStatus('Failed', 'fail');
  } finally {
    setAiButtonsDisabled(false);
  }
}

async function testAiConnection() {
  const { baseUrl, model } = currentAiFormState();
  if (!baseUrl || !validUrl(baseUrl)) {
    setAiMessage('Base URL must be a valid http(s) URL.', true);
    return;
  }
  if (!model) {
    setAiMessage('Please select a model before testing connection.', true);
    return;
  }

  setAiButtonsDisabled(true);
  setAiMessage('Testing Ollama connection...');

  try {
    const response = await fetch('/api/ai/test', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ baseUrl, model })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'Connection failed');
    setAiStatus('Connected', 'ok');
    setAiMessage(data.message || 'Connection successful.');
  } catch (error) {
    setAiStatus('Failed', 'fail');
    setAiMessage(error.message || 'Unable to connect to Ollama.', true);
  } finally {
    setAiButtonsDisabled(false);
  }
}

async function saveAiConfig() {
  const { baseUrl, model } = currentAiFormState();
  if (!baseUrl || !validUrl(baseUrl)) {
    setAiMessage('Please enter a valid Base URL (http:// or https://).', true);
    return;
  }
  if (!model) {
    setAiMessage('Please select an Ollama model.', true);
    return;
  }

  setAiButtonsDisabled(true);
  setAiMessage('Saving AI configuration...');

  try {
    await fetchJsonOrThrow('/api/ai/config', {
      method: 'POST',
      body: JSON.stringify({ baseUrl, model })
    });
    setAiMessage('Configuration saved successfully.');
  } catch (error) {
    setAiMessage(error.message || 'Unable to save configuration.', true);
  } finally {
    setAiButtonsDisabled(false);
  }
}

function wireAiSettingsEvents() {
  document.getElementById('aiRefreshModelsBtn')?.addEventListener('click', refreshModels);
  document.getElementById('aiTestConnectionBtn')?.addEventListener('click', testAiConnection);
  document.getElementById('aiSaveConfigBtn')?.addEventListener('click', saveAiConfig);
}

document.addEventListener('DOMContentLoaded', async () => {
  try {
    await Promise.all([loadProfileSettings(), loadSlaPolicies(), loadAssetDetectionPrefixes()]);
    wireAiSettingsEvents();
    await loadAiConfig();
  } catch (error) {
    alert(error.message || 'Unable to load settings data');
  }
});

async function changeMyPassword() {
  const body = {
    currentPassword: document.getElementById('currentPassword')?.value || '',
    newPassword: document.getElementById('newPassword')?.value || '',
    confirmPassword: document.getElementById('confirmNewPassword')?.value || ''
  };
  const res = await fetch('/api/users/me/password', {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to change password');
  alert(data.message || 'Password changed successfully');
  document.getElementById('currentPassword').value = '';
  document.getElementById('newPassword').value = '';
  document.getElementById('confirmNewPassword').value = '';
}
