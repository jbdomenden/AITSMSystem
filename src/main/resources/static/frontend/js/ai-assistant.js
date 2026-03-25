const AI_CHAT_STATE_KEY = 'aiAssistantChatState';
const AI_TICKET_PREFILL_KEY = 'aiTicketPrefill';
const AI_SESSION_KEY = 'aiAssistantSessionId';

let aiState = {
  messages: [],
  pending: false,
  lastVisible: null
};

function getSessionId() {
  let id = localStorage.getItem(AI_SESSION_KEY);
  if (id) return id;
  id = (crypto?.randomUUID?.() || `sess-${Date.now()}-${Math.random().toString(16).slice(2)}`);
  localStorage.setItem(AI_SESSION_KEY, id);
  return id;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function saveState() {
  try {
    localStorage.setItem(AI_CHAT_STATE_KEY, JSON.stringify(aiState));
  } catch {
    // noop
  }
}

function loadState() {
  try {
    const raw = localStorage.getItem(AI_CHAT_STATE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed.messages)) return;
    aiState.messages = parsed.messages.slice(-20);
    aiState.lastVisible = parsed.lastVisible || null;
  } catch {
    aiState.messages = [];
    aiState.lastVisible = null;
  }
}

function renderFallbackSections(fallback = {}) {
  const causes = (fallback.likelyCauses || []).map((item) => `<li>${escapeHtml(item)}</li>`).join('');
  const steps = (fallback.troubleshootingSteps || []).map((item) => `<li>${escapeHtml(item)}</li>`).join('');
  const escalate = (fallback.escalationCriteria || []).map((item) => `<li>${escapeHtml(item)}</li>`).join('');

  return `
    <div class='ai-sections'>
      <div><strong>Issue Summary</strong><p>${escapeHtml(fallback.issueSummary || 'Not provided')}</p></div>
      <div><strong>Likely Causes</strong><ul>${causes || '<li>No likely causes provided.</li>'}</ul></div>
      <div><strong>Troubleshooting Steps</strong><ol>${steps || '<li>No troubleshooting steps provided.</li>'}</ol></div>
      <div><strong>Escalation Criteria</strong><ul>${escalate || '<li>No escalation guidance provided.</li>'}</ul></div>
      <div><strong>Suggested Priority</strong><p>${escapeHtml(fallback.suggestedPriority || 'Medium')}</p></div>
    </div>`;
}

function renderMessages() {
  const thread = document.getElementById('aiThread');
  if (!thread) return;

  if (!aiState.messages.length) {
    thread.innerHTML = "<p class='small'>Start by describing your issue. The assistant will return troubleshooting guidance.</p>";
  } else {
    thread.innerHTML = aiState.messages.map((message) => {
      if (message.role === 'user') {
        return `<article class='ai-message ai-message-user'><div class='ai-message-bubble'>${escapeHtml(message.content).replaceAll('\n', '<br>')}</div></article>`;
      }
      return `<article class='ai-message ai-message-assistant'><div class='ai-message-bubble'><p>${escapeHtml(message.content || '').replaceAll('\n', '<br>')}</p></div></article>`;
    }).join('');
  }

  thread.scrollTop = thread.scrollHeight;
}

function renderFallbackPanel() {
  const panel = document.getElementById('aiFallbackPanel');
  const message = document.getElementById('aiFallbackMessage');
  const details = document.getElementById('aiFallbackDetails');
  if (!panel || !message || !details) return;

  const visible = aiState.lastVisible;
  if (!visible || visible.type !== 'fallback' || !visible.fallback) {
    panel.classList.add('hidden');
    message.textContent = '';
    details.innerHTML = '';
    return;
  }

  panel.classList.remove('hidden');
  message.textContent = visible.fallback.message || 'AI backend is unavailable.';
  details.innerHTML = renderFallbackSections(visible.fallback);
}

function setPending(isPending) {
  aiState.pending = isPending;
  document.getElementById('aiSendBtn')?.toggleAttribute('disabled', isPending);
  const textarea = document.getElementById('aiMessage');
  if (textarea) textarea.disabled = isPending;
  document.getElementById('aiLoading')?.classList.toggle('hidden', !isPending);
}

function addUserMessage(content) {
  aiState.messages.push({
    role: 'user',
    content: String(content || '').trim(),
    timestamp: new Date().toISOString()
  });
  aiState.messages = aiState.messages.slice(-20);
  saveState();
  renderMessages();
}

function addAssistantReply(replyText) {
  aiState.messages.push({
    role: 'assistant',
    content: String(replyText || '').trim(),
    timestamp: new Date().toISOString()
  });
  aiState.messages = aiState.messages.slice(-20);
}

function applyAiResponse(payload, userMessage) {
  if (!payload || typeof payload !== 'object') {
    aiState.lastVisible = {
      type: 'fallback',
      fallback: localFallback('Malformed AI response payload.', userMessage)
    };
    saveState();
    renderMessages();
    renderFallbackPanel();
    return;
  }

  const source = payload.source;
  if (source === 'ollama' && typeof payload.reply === 'string' && payload.reply.trim()) {
    addAssistantReply(payload.reply.trim());
    aiState.lastVisible = { type: 'ollama', reply: payload.reply.trim() };
    saveState();
    renderMessages();
    renderFallbackPanel();
    return;
  }

  if (source === 'fallback' && payload.fallback && typeof payload.fallback === 'object') {
    aiState.lastVisible = { type: 'fallback', fallback: payload.fallback };
    saveState();
    renderMessages();
    renderFallbackPanel();
    return;
  }

  aiState.lastVisible = {
    type: 'fallback',
    fallback: localFallback('AI response shape was invalid.', userMessage)
  };
  saveState();
  renderMessages();
  renderFallbackPanel();
}

function localFallback(reason, userMessage) {
  return {
    message: 'I’m currently unable to process a full diagnosis. Please verify local Ollama connectivity and retry.',
    issueSummary: userMessage || 'Unable to process request',
    likelyCauses: [reason || 'Backend-to-Ollama connectivity issue'],
    troubleshootingSteps: ['Verify Ollama is running locally', 'Check configured model in Settings', 'Retry request'],
    escalationCriteria: ['Escalate if service remains unavailable for critical support cases'],
    suggestedPriority: 'Medium',
    ticketTitle: 'AI assistant unavailable',
    ticketDescription: 'AI assistant request failed because backend could not reach Ollama.'
  };
}

async function sendMessage() {
  if (aiState.pending) return;
  const textarea = document.getElementById('aiMessage');
  if (!textarea) return;
  const message = textarea.value.trim();
  if (!message) return;

  addUserMessage(message);
  textarea.value = '';
  setPending(true);

  try {
    const res = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: {
        ...authHeaders(),
        'X-AI-Session-Id': getSessionId()
      },
      body: JSON.stringify({ message })
    });

    const data = await res.json().catch(() => null);
    if (!res.ok) {
      aiState.lastVisible = { type: 'fallback', fallback: localFallback(data?.error || data?.message || 'Unable to contact AI assistant', message) };
      saveState();
      renderFallbackPanel();
      return;
    }
    applyAiResponse(data, message);
  } catch (error) {
    aiState.lastVisible = { type: 'fallback', fallback: localFallback(error?.message || 'Unable to contact AI assistant', message) };
    saveState();
    renderFallbackPanel();
  } finally {
    setPending(false);
    textarea.focus();
  }
}

async function clearChat() {
  aiState = { messages: [], pending: false, lastVisible: null };
  saveState();
  renderMessages();
  renderFallbackPanel();

  try {
    await fetch('/api/ai/clear', {
      method: 'POST',
      headers: {
        ...authHeaders(),
        'X-AI-Session-Id': getSessionId()
      }
    });
  } catch {
    // local clear is enough
  }
}

function draftFromVisible() {
  const visible = aiState.lastVisible;
  const lastUser = aiState.messages.filter((m) => m.role === 'user').slice(-1)[0]?.content || '';
  if (!visible) return null;

  if (visible.type === 'ollama' && visible.reply) {
    return {
      title: 'AI-assisted IT support request',
      description: visible.reply,
      priority: 'Medium',
      issueSummary: lastUser || 'IT support request',
      originalUserMessage: lastUser
    };
  }

  if (visible.type === 'fallback' && visible.fallback) {
    return {
      title: visible.fallback.ticketTitle || visible.fallback.issueSummary || 'IT support request',
      description: visible.fallback.ticketDescription || visible.fallback.message || '',
      priority: visible.fallback.suggestedPriority || 'Medium',
      issueSummary: visible.fallback.issueSummary || lastUser,
      originalUserMessage: lastUser
    };
  }

  return null;
}

async function createDraftFromVisible() {
  const draft = draftFromVisible();
  if (!draft) throw new Error('No assistant response available yet.');

  const res = await fetch('/api/ai/create-ticket-draft', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({
      issueSummary: draft.issueSummary,
      ticketDescription: draft.description,
      suggestedPriority: draft.priority,
      originalUserMessage: draft.originalUserMessage,
      ticketTitle: draft.title
    })
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Unable to build ticket draft');

  sessionStorage.setItem(AI_TICKET_PREFILL_KEY, JSON.stringify({
    title: data.title,
    description: data.description,
    priority: data.priority
  }));
  location.href = '/create-ticket.html';
}

function usePrompt(prompt) {
  const textarea = document.getElementById('aiMessage');
  if (!textarea) return;
  textarea.value = prompt;
  textarea.focus();
}

function applyTicketPrefillFromAI() {
  const title = document.getElementById('title');
  const description = document.getElementById('description');
  const priority = document.getElementById('priority');
  if (!title || !description || !priority) return;

  const raw = sessionStorage.getItem(AI_TICKET_PREFILL_KEY);
  if (!raw) return;

  try {
    const prefill = JSON.parse(raw);
    if (prefill.title) title.value = String(prefill.title).slice(0, 255);
    if (prefill.description) description.value = String(prefill.description).slice(0, 5000);
    if (prefill.priority) priority.value = prefill.priority;
  } finally {
    sessionStorage.removeItem(AI_TICKET_PREFILL_KEY);
  }
}

function wireEvents() {
  document.getElementById('aiComposer')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    await sendMessage();
  });

  document.getElementById('aiMessage')?.addEventListener('keydown', async (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      await sendMessage();
    }
  });

  document.getElementById('clearAiChatBtn')?.addEventListener('click', clearChat);
  document.querySelectorAll('.ai-prompt').forEach((button) => {
    button.addEventListener('click', () => usePrompt(button.dataset.prompt || ''));
  });

  document.getElementById('createTicketFromLastBtn')?.addEventListener('click', async () => {
    try {
      await createDraftFromVisible();
    } catch (error) {
      alert(error?.message || 'Unable to create ticket draft');
    }
  });

  document.getElementById('aiFallbackTicketBtn')?.addEventListener('click', async () => {
    try {
      await createDraftFromVisible();
    } catch (error) {
      alert(error?.message || 'Unable to create ticket draft');
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  applyTicketPrefillFromAI();
  if (!document.getElementById('aiComposer')) return;
  loadState();
  renderMessages();
  renderFallbackPanel();
  wireEvents();
});
