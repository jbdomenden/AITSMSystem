const AI_CHAT_STATE_KEY = 'aiAssistantChatState';
const AI_TICKET_PREFILL_KEY = 'aiTicketPrefill';
const AI_SESSION_KEY = 'aiAssistantSessionId';

let aiState = {
  messages: [],
  pending: false,
  lastDraft: null
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

function formatTime(isoValue) {
  const date = new Date(isoValue || Date.now());
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
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
    aiState.messages = parsed.messages
      .filter((msg) => msg && typeof msg.role === 'string' && typeof msg.content === 'string')
      .slice(-20);
    aiState.lastDraft = parsed.lastDraft || null;
  } catch {
    aiState.messages = [];
    aiState.lastDraft = null;
  }
}

function renderMessages() {
  const thread = document.getElementById('aiThread');
  if (!thread) return;

  if (!aiState.messages.length) {
    thread.innerHTML = "<p class='small'>Start by describing your issue. The assistant will respond using real model output and suggest a ticket draft.</p>";
    return;
  }

  thread.innerHTML = aiState.messages.map((message, index) => {
    const isUser = message.role === 'user';
    const canCreateTicket = !isUser && message.ticketSuggestion;
    return `
      <article class='ai-message ${isUser ? 'ai-message-user' : 'ai-message-assistant'}'>
        <div class='ai-message-bubble'>${escapeHtml(message.content).replaceAll('\n', '<br>')}</div>
        <div class='ai-message-meta'>${isUser ? 'You' : 'AI Assistant'} • ${formatTime(message.timestamp)}</div>
        ${canCreateTicket ? `<button type='button' class='btn btn-ghost ai-ticket-btn' data-index='${index}'>Create ticket draft from this reply</button>` : ''}
      </article>`;
  }).join('');

  thread.scrollTop = thread.scrollHeight;
}

function setPending(isPending) {
  aiState.pending = isPending;
  const sendBtn = document.getElementById('aiSendBtn');
  const loading = document.getElementById('aiLoading');
  const textarea = document.getElementById('aiMessage');

  if (sendBtn) sendBtn.disabled = isPending;
  if (textarea) textarea.disabled = isPending;
  loading?.classList.toggle('hidden', !isPending);
}

function addMessage(role, content, extra = {}) {
  const text = String(content || '').trim();
  if (!text) return;
  aiState.messages.push({
    role,
    content: text,
    timestamp: new Date().toISOString(),
    ...extra
  });
  aiState.messages = aiState.messages.slice(-20);
  saveState();
  renderMessages();
}

async function sendMessage() {
  if (aiState.pending) return;
  const textarea = document.getElementById('aiMessage');
  if (!textarea) return;
  const message = textarea.value.trim();
  if (!message) return;

  addMessage('user', message);
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
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Unable to contact AI assistant');

    const structured = data.structured || {};
    const ticketSuggestion = {
      title: data.titleSuggestion || structured.issueSummary || 'IT support request',
      description: data.descriptionSuggestion || structured.ticketDescription || data.reply || '',
      category: data.category || structured.suggestedCategory || 'Other',
      priority: data.priority || structured.suggestedPriority || 'Medium',
      originalUserMessage: message,
      issueSummary: structured.issueSummary || ''
    };
    aiState.lastDraft = ticketSuggestion;

    addMessage('assistant', data.reply || 'No response received.', {
      ticketSuggestion,
      model: data.model || ''
    });
  } catch (error) {
    addMessage('assistant', `AI service is currently unavailable. ${error?.message || 'Please try again in a moment.'}`);
  } finally {
    setPending(false);
    textarea.focus();
  }
}

async function clearChat() {
  aiState = { messages: [], pending: false, lastDraft: null };
  saveState();
  renderMessages();

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

function usePrompt(prompt) {
  const textarea = document.getElementById('aiMessage');
  if (!textarea) return;
  textarea.value = prompt;
  textarea.focus();
}

async function createDraftFromSuggestion(suggestion) {
  if (!suggestion) return;
  const res = await fetch('/api/ai/create-ticket-draft', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({
      issueSummary: suggestion.issueSummary || suggestion.title,
      ticketDescription: suggestion.description,
      suggestedPriority: suggestion.priority,
      suggestedCategory: suggestion.category,
      originalUserMessage: suggestion.originalUserMessage || null
    })
  });

  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Unable to build ticket draft');

  sessionStorage.setItem(AI_TICKET_PREFILL_KEY, JSON.stringify({
    title: data.title,
    description: data.description,
    category: data.category,
    priority: data.priority
  }));
  location.href = '/create-ticket.html';
}

function wireEvents() {
  const form = document.getElementById('aiComposer');
  const textarea = document.getElementById('aiMessage');

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    await sendMessage();
  });

  textarea?.addEventListener('keydown', async (event) => {
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
      await createDraftFromSuggestion(aiState.lastDraft);
    } catch (error) {
      addMessage('assistant', `Could not prepare ticket draft. ${error?.message || ''}`);
    }
  });

  document.getElementById('aiThread')?.addEventListener('click', async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    if (!target.classList.contains('ai-ticket-btn')) return;
    const index = Number(target.dataset.index);
    if (Number.isNaN(index)) return;
    const suggestion = aiState.messages[index]?.ticketSuggestion;
    try {
      await createDraftFromSuggestion(suggestion);
    } catch (error) {
      addMessage('assistant', `Could not prepare ticket draft. ${error?.message || ''}`);
    }
  });
}

function applyTicketPrefillFromAI() {
  const title = document.getElementById('title');
  const description = document.getElementById('description');
  const category = document.getElementById('category');
  const priority = document.getElementById('priority');
  if (!title || !description || !category || !priority) return;

  const raw = sessionStorage.getItem(AI_TICKET_PREFILL_KEY);
  if (!raw) return;

  try {
    const prefill = JSON.parse(raw);
    if (prefill.title) title.value = String(prefill.title).slice(0, 255);
    if (prefill.description) description.value = String(prefill.description).slice(0, 5000);
    if (prefill.category) category.value = prefill.category;
    if (prefill.priority) priority.value = prefill.priority;
  } catch {
    // noop
  } finally {
    sessionStorage.removeItem(AI_TICKET_PREFILL_KEY);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  applyTicketPrefillFromAI();

  if (!document.getElementById('aiComposer')) return;
  loadState();
  renderMessages();
  wireEvents();
});
