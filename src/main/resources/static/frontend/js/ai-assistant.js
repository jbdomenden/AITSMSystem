const AI_CHAT_STATE_KEY = 'aiAssistantChatState';
const AI_TICKET_PREFILL_KEY = 'aiTicketPrefill';

let aiState = {
  messages: [],
  pending: false
};

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
  } catch {
    aiState.messages = [];
  }
}

function renderMessages() {
  const thread = document.getElementById('aiThread');
  if (!thread) return;

  if (!aiState.messages.length) {
    thread.innerHTML = "<p class='small'>Start by describing your issue. The assistant will provide troubleshooting steps and ticket suggestions.</p>";
    return;
  }

  thread.innerHTML = aiState.messages.map((message, index) => {
    const isUser = message.role === 'user';
    const canCreateTicket = !isUser && message.ticketSuggestion;
    return `
      <article class='ai-message ${isUser ? 'ai-message-user' : 'ai-message-assistant'}'>
        <div class='ai-message-bubble'>${escapeHtml(message.content).replaceAll('\n', '<br>')}</div>
        <div class='ai-message-meta'>${isUser ? 'You' : 'AI Assistant'} • ${formatTime(message.timestamp)}</div>
        ${canCreateTicket ? `<button type='button' class='btn btn-ghost ai-ticket-btn' data-index='${index}'>Use this to create ticket</button>` : ''}
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

function toHistoryPayload() {
  return aiState.messages
    .slice(-10)
    .map((message) => ({ role: message.role, content: message.content }));
}

function addMessage(role, content, extra = {}) {
  aiState.messages.push({
    role,
    content: String(content || '').trim(),
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
    const res = await fetch('/api/ai-assistant/chat', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        message,
        history: toHistoryPayload()
      })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Unable to contact AI assistant');

    addMessage('assistant', data.reply || 'No response received.', {
      ticketSuggestion: {
        title: data.titleSuggestion || 'IT support request',
        description: data.descriptionSuggestion || data.reply || '',
        category: data.category || 'Other',
        priority: data.priority || 'Medium'
      },
      fallback: Boolean(data.fallback)
    });
  } catch (error) {
    addMessage('assistant', `I could not process that right now. ${error?.message || 'Please try again.'}`);
  } finally {
    setPending(false);
    textarea.focus();
  }
}

function clearChat() {
  aiState = { messages: [], pending: false };
  saveState();
  renderMessages();
}

function usePrompt(prompt) {
  const textarea = document.getElementById('aiMessage');
  if (!textarea) return;
  textarea.value = prompt;
  textarea.focus();
}

function storePrefillAndNavigate(index) {
  const message = aiState.messages[index];
  const suggestion = message?.ticketSuggestion;
  if (!suggestion) return;

  sessionStorage.setItem(AI_TICKET_PREFILL_KEY, JSON.stringify({
    title: suggestion.title,
    description: suggestion.description,
    category: suggestion.category,
    priority: suggestion.priority
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

  document.getElementById('aiThread')?.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    if (!target.classList.contains('ai-ticket-btn')) return;
    const index = Number(target.dataset.index);
    if (Number.isNaN(index)) return;
    storePrefillAndNavigate(index);
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
