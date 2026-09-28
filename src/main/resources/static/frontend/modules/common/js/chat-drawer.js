(function initializeAitsmChatDrawer() {
  if (window.AITSMChat || !currentRole()) return;

  const STORAGE_KEY = 'aitsm.chat.drawer.v1';
  const POLL_VISIBLE_MS = 4000;
  const POLL_HIDDEN_MS = 15000;
  const defaultState = { display: 'closed', selectedId: null, drafts: {}, scrollPositions: {} };
  let state = loadState();
  let conversations = [];
  let root = null;
  let pollTimer = null;
  let sending = false;
  let lastFocusedElement = null;
  let lastRenderedMessageId = null;

  function loadState() {
    try {
      const stored = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '{}');
      return {
        ...defaultState,
        ...stored,
        drafts: stored.drafts && typeof stored.drafts === 'object' ? stored.drafts : {},
        scrollPositions: stored.scrollPositions && typeof stored.scrollPositions === 'object' ? stored.scrollPositions : {}
      };
    } catch {
      return { ...defaultState };
    }
  }

  function saveState() {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  async function api(url, options = {}) {
    const response = await fetch(url, {
      ...options,
      headers: { ...authHeaders(), ...(options.headers || {}) }
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || data.message || 'Chat is temporarily unavailable.');
    return data;
  }

  function currentUserId() {
    return Number(localStorage.getItem('userId') || 0);
  }

  function formatTime(value) {
    if (!value) return '';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  function formatConversationTime(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const today = new Date();
    return date.toDateString() === today.toDateString()
      ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  }

  function createAvatar(colleague) {
    const avatar = document.createElement('span');
    avatar.className = 'chat-avatar';
    avatar.setAttribute('aria-hidden', 'true');
    avatar.textContent = String(colleague?.fullName || '?').trim().charAt(0).toUpperCase() || '?';
    if (colleague?.profilePhotoUrl) {
      const image = document.createElement('img');
      image.src = colleague.profilePhotoUrl;
      image.alt = '';
      image.addEventListener('error', () => image.remove());
      avatar.appendChild(image);
    }
    return avatar;
  }

  function mount() {
    if (root || !document.body) return;
    root = document.createElement('section');
    root.id = 'aitsmChatDrawer';
    root.className = 'chat-drawer-shell hidden';
    root.setAttribute('aria-label', 'AITSM chat');
    root.innerHTML = `
      <button class="chat-minimized-bar" type="button" aria-label="Restore chat">
        <span aria-hidden="true">💬</span><span>Chat</span><span class="chat-minimized-count hidden">0</span>
      </button>
      <div class="chat-drawer" role="dialog" aria-modal="false" aria-labelledby="chatDrawerTitle">
        <header class="chat-drawer-header">
          <div>
            <strong id="chatDrawerTitle">Messages</strong>
            <span class="chat-drawer-status">Connected colleagues</span>
          </div>
          <div class="chat-drawer-controls">
            <button class="chat-drawer-icon" type="button" data-chat-minimize aria-label="Minimize chat" title="Minimize chat">−</button>
            <button class="chat-drawer-icon" type="button" data-chat-close aria-label="Close chat" title="Close chat">×</button>
          </div>
        </header>
        <div class="chat-drawer-body">
          <section class="chat-conversation-view" data-chat-view="list" aria-label="Conversations">
            <div class="chat-drawer-section-head"><strong>Conversations</strong><a href="/colleagues.html">Manage colleagues</a></div>
            <div class="chat-drawer-list" data-chat-list></div>
          </section>
          <section class="chat-conversation-view hidden" data-chat-view="conversation" aria-label="Active conversation">
            <div class="chat-active-header">
              <button class="chat-drawer-icon" type="button" data-chat-back aria-label="Back to conversations" title="Back to conversations">←</button>
              <div class="chat-active-person" data-chat-person></div>
              <button class="chat-profile-link" type="button" data-chat-profile>Profile</button>
            </div>
            <div class="chat-message-list" data-chat-messages role="log" aria-live="polite" aria-relevant="additions"></div>
            <form class="chat-drawer-form" data-chat-form>
              <label class="sr-only" for="chatDrawerMessage">Message</label>
              <textarea id="chatDrawerMessage" maxlength="2000" rows="2" placeholder="Write a message" required></textarea>
              <button class="btn btn-primary" type="submit">Send</button>
            </form>
            <p class="chat-send-status small" data-chat-send-status aria-live="polite"></p>
          </section>
        </div>
      </div>`;
    document.body.appendChild(root);

    root.querySelector('[data-chat-minimize]')?.addEventListener('click', minimize);
    root.querySelector('[data-chat-close]')?.addEventListener('click', close);
    root.querySelector('.chat-minimized-bar')?.addEventListener('click', restore);
    root.querySelector('[data-chat-back]')?.addEventListener('click', showConversationList);
    root.querySelector('[data-chat-profile]')?.addEventListener('click', openSelectedProfile);
    root.querySelector('[data-chat-form]')?.addEventListener('submit', sendMessage);
    root.querySelector('#chatDrawerMessage')?.addEventListener('input', saveDraft);
    root.querySelector('[data-chat-messages]')?.addEventListener('scroll', saveScrollPosition);
    document.addEventListener('keydown', handleKeydown);
    applyDisplayState();
    refreshConversations({ includeMessages: state.display === 'open' }).catch(showListError);
    schedulePoll();
  }

  function updateTriggerState() {
    const expanded = state.display === 'open';
    ['userChatTrigger', 'adminChatTrigger', 'globalChatTrigger'].forEach((id) => {
      document.getElementById(id)?.setAttribute('aria-expanded', String(expanded));
    });
  }

  function applyDisplayState() {
    if (!root) return;
    root.classList.toggle('hidden', state.display === 'closed');
    root.classList.toggle('is-open', state.display === 'open');
    root.classList.toggle('is-minimized', state.display === 'minimized');
    root.querySelector('.chat-drawer')?.setAttribute('aria-modal', String(window.matchMedia('(max-width: 640px)').matches && state.display === 'open'));
    updateTriggerState();
    if (state.display === 'open') {
      renderCurrentView();
      window.setTimeout(() => {
        const focusTarget = state.selectedId
          ? root?.querySelector('#chatDrawerMessage')
          : root?.querySelector('.chat-conversation-row');
        focusTarget?.focus();
      }, 0);
    }
  }

  function open(colleagueId = null) {
    lastFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    state.display = 'open';
    if (Number(colleagueId) > 0) state.selectedId = Number(colleagueId);
    saveState();
    mount();
    applyDisplayState();
    refreshConversations({ includeMessages: true }).catch(showListError);
  }

  function restore() {
    state.display = 'open';
    saveState();
    applyDisplayState();
    refreshConversations({ includeMessages: true }).catch(showListError);
  }

  function minimize() {
    persistComposerState();
    state.display = 'minimized';
    saveState();
    applyDisplayState();
    root?.querySelector('.chat-minimized-bar')?.focus();
  }

  function close() {
    persistComposerState();
    state.display = 'closed';
    saveState();
    applyDisplayState();
    lastFocusedElement?.focus?.();
  }

  function showConversationList() {
    persistComposerState();
    state.selectedId = null;
    lastRenderedMessageId = null;
    saveState();
    renderCurrentView();
  }

  function openSelectedProfile() {
    if (!state.selectedId) return;
    location.href = colleagueProfileUrl(state.selectedId);
  }

  function handleKeydown(event) {
    if (event.key === 'Escape' && state.display === 'open' && !document.getElementById('globalSearchModal')?.classList.contains('show')) {
      event.preventDefault();
      minimize();
      return;
    }
    if (event.key === 'Tab' && state.display === 'open' && window.matchMedia('(max-width: 640px)').matches && root) {
      const focusable = [...root.querySelectorAll('button:not([disabled]), a[href], textarea:not([disabled])')]
        .filter((element) => !element.closest('.hidden') && element.offsetParent !== null);
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  }

  function saveDraft(event) {
    if (!state.selectedId) return;
    state.drafts[state.selectedId] = event.target.value;
    saveState();
  }

  function saveScrollPosition(event) {
    if (!state.selectedId) return;
    state.scrollPositions[state.selectedId] = event.target.scrollTop;
    saveState();
  }

  function persistComposerState() {
    if (!state.selectedId || !root) return;
    const composer = root.querySelector('#chatDrawerMessage');
    const messageList = root.querySelector('[data-chat-messages]');
    if (composer) state.drafts[state.selectedId] = composer.value;
    if (messageList) state.scrollPositions[state.selectedId] = messageList.scrollTop;
    saveState();
  }

  function selectedConversation() {
    return conversations.find((item) => Number(item.colleague?.id) === Number(state.selectedId));
  }

  function renderCurrentView() {
    if (!root || state.display !== 'open') return;
    const listView = root.querySelector('[data-chat-view="list"]');
    const conversationView = root.querySelector('[data-chat-view="conversation"]');
    const selected = selectedConversation();
    if (state.selectedId && selected) {
      listView?.classList.add('hidden');
      conversationView?.classList.remove('hidden');
      renderActivePerson(selected.colleague);
      const composer = root.querySelector('#chatDrawerMessage');
      if (composer && composer.value !== (state.drafts[state.selectedId] || '')) composer.value = state.drafts[state.selectedId] || '';
    } else {
      if (state.selectedId && conversations.length) state.selectedId = null;
      listView?.classList.remove('hidden');
      conversationView?.classList.add('hidden');
      renderConversationList();
    }
  }

  function renderActivePerson(colleague) {
    const host = root?.querySelector('[data-chat-person]');
    if (!host) return;
    host.replaceChildren(createAvatar(colleague));
    const text = document.createElement('span');
    const name = document.createElement('strong');
    const detail = document.createElement('small');
    name.textContent = colleague.fullName || 'Colleague';
    detail.textContent = `${colleague.department || 'Department pending'} · ${String(colleague.role || '').replaceAll('_', ' ')}`;
    text.append(name, detail);
    host.appendChild(text);
  }

  function renderConversationList() {
    const list = root?.querySelector('[data-chat-list]');
    if (!list) return;
    list.replaceChildren();
    if (!conversations.length) {
      const empty = document.createElement('div');
      empty.className = 'chat-empty-state';
      const title = document.createElement('strong');
      const message = document.createElement('span');
      title.textContent = 'No conversations yet';
      message.textContent = 'Your accepted colleagues will appear here.';
      empty.append(title, message);
      list.appendChild(empty);
      return;
    }

    conversations.forEach((item) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'chat-conversation-row';
      if (item.unreadCount) button.classList.add('has-unread');
      button.appendChild(createAvatar(item.colleague));
      const copy = document.createElement('span');
      copy.className = 'chat-conversation-copy';
      const top = document.createElement('span');
      top.className = 'chat-conversation-topline';
      const name = document.createElement('strong');
      const time = document.createElement('small');
      name.textContent = item.colleague?.fullName || 'Colleague';
      time.textContent = formatConversationTime(item.lastMessage?.sentAt);
      top.append(name, time);
      const preview = document.createElement('small');
      preview.textContent = item.lastMessage?.body || `${item.colleague?.department || 'Connected colleague'} · Start a conversation`;
      copy.append(top, preview);
      button.appendChild(copy);
      if (item.unreadCount) {
        const unread = document.createElement('span');
        unread.className = 'chat-unread-pill';
        unread.textContent = String(item.unreadCount);
        unread.setAttribute('aria-label', `${item.unreadCount} unread messages`);
        button.appendChild(unread);
      }
      button.addEventListener('click', () => selectConversation(item.colleague.id));
      list.appendChild(button);
    });
  }

  function selectConversation(id) {
    persistComposerState();
    state.selectedId = Number(id);
    state.display = 'open';
    lastRenderedMessageId = null;
    saveState();
    applyDisplayState();
    fetchMessages(state.selectedId).catch(showMessageError);
  }

  function showListError(error) {
    const list = root?.querySelector('[data-chat-list]');
    if (!list) return;
    list.replaceChildren();
    const empty = document.createElement('div');
    empty.className = 'chat-empty-state is-error';
    empty.textContent = error?.message || 'Messages could not be loaded. Try again.';
    list.appendChild(empty);
  }

  function showMessageError(error) {
    const list = root?.querySelector('[data-chat-messages]');
    if (!list) return;
    list.replaceChildren();
    const empty = document.createElement('div');
    empty.className = 'chat-empty-state is-error';
    empty.textContent = error?.message || 'Messages could not be loaded. Try again.';
    list.appendChild(empty);
  }

  function unreadTotal() {
    return conversations.reduce((total, item) => total + Number(item.unreadCount || 0), 0);
  }

  function updateUnreadBadges() {
    const total = unreadTotal();
    ['userChatCount', 'adminChatCount', 'globalChatCount'].forEach((id) => {
      const badge = document.getElementById(id);
      if (!badge) return;
      badge.textContent = String(total);
      badge.classList.toggle('hidden', total === 0);
    });
    const minimized = root?.querySelector('.chat-minimized-count');
    if (minimized) {
      minimized.textContent = String(total);
      minimized.classList.toggle('hidden', total === 0);
    }
  }

  function publishConversations() {
    document.dispatchEvent(new CustomEvent('aitsm:chat-conversations', { detail: { conversations: [...conversations] } }));
  }

  function acknowledgeRead(colleagueId) {
    const conversation = conversations.find((item) => Number(item.colleague?.id) === Number(colleagueId));
    if (!conversation || !conversation.unreadCount) return;
    conversation.unreadCount = 0;
    updateUnreadBadges();
    publishConversations();
  }

  async function refreshConversations({ includeMessages = true } = {}) {
    const data = await api('/api/colleagues/messages/conversations');
    conversations = Array.isArray(data) ? data : [];
    if (state.selectedId && !selectedConversation()) state.selectedId = null;
    updateUnreadBadges();
    publishConversations();
    renderCurrentView();
    if (includeMessages && state.display === 'open' && state.selectedId) await fetchMessages(state.selectedId);
    return conversations;
  }

  function renderMessages(messages, colleagueId) {
    if (!root || Number(state.selectedId) !== Number(colleagueId) || state.display !== 'open') return;
    const list = root.querySelector('[data-chat-messages]');
    if (!list) return;
    const wasNearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 72;
    const newestId = messages.at(-1)?.id || null;
    const firstRender = lastRenderedMessageId === null;
    list.replaceChildren();
    if (!messages.length) {
      const empty = document.createElement('div');
      empty.className = 'chat-empty-state';
      empty.textContent = 'No messages yet. Say hello to start the conversation.';
      list.appendChild(empty);
    } else {
      messages.forEach((message) => {
        const bubble = document.createElement('div');
        bubble.className = `chat-message${Number(message.senderId) === currentUserId() ? ' mine' : ''}`;
        const body = document.createElement('p');
        const time = document.createElement('time');
        body.textContent = message.body || '';
        time.textContent = formatTime(message.sentAt);
        time.dateTime = message.sentAt || '';
        bubble.append(body, time);
        list.appendChild(bubble);
      });
    }
    requestAnimationFrame(() => {
      if (firstRender) {
        const stored = Number(state.scrollPositions[colleagueId]);
        list.scrollTop = Number.isFinite(stored) && stored > 0 ? stored : list.scrollHeight;
      } else if (wasNearBottom) {
        list.scrollTop = list.scrollHeight;
      }
    });
    lastRenderedMessageId = newestId;
  }

  async function fetchMessages(colleagueId) {
    if (state.display !== 'open' || Number(state.selectedId) !== Number(colleagueId)) return [];
    const messages = await api(`/api/colleagues/${Number(colleagueId)}/messages`);
    renderMessages(Array.isArray(messages) ? messages : [], colleagueId);
    await api(`/api/notifications/direct-messages/${Number(colleagueId)}/read`, { method: 'PATCH' }).catch(() => null);
    window.loadUserHeaderNotifications?.();
    window.loadHeaderNotifications?.();
    const selected = selectedConversation();
    if (selected?.unreadCount) {
      acknowledgeRead(colleagueId);
      document.dispatchEvent(new CustomEvent('aitsm:chat-read', { detail: { colleagueId: Number(colleagueId) } }));
    }
    return messages;
  }

  async function sendMessage(event) {
    event.preventDefault();
    if (!state.selectedId || sending) return;
    const composer = root?.querySelector('#chatDrawerMessage');
    const submit = root?.querySelector('[data-chat-form] button[type="submit"]');
    const status = root?.querySelector('[data-chat-send-status]');
    const body = composer?.value.trim() || '';
    if (!body) return;
    sending = true;
    if (submit) submit.disabled = true;
    if (status) status.textContent = 'Sending…';
    try {
      await api(`/api/colleagues/${state.selectedId}/messages`, { method: 'POST', body: JSON.stringify({ body }) });
      if (composer) composer.value = '';
      delete state.drafts[state.selectedId];
      saveState();
      if (status) status.textContent = '';
      await refreshConversations({ includeMessages: true });
      const messageList = root?.querySelector('[data-chat-messages]');
      if (messageList) messageList.scrollTop = messageList.scrollHeight;
    } catch (error) {
      if (status) status.textContent = error.message || 'Message failed to send. Try again.';
    } finally {
      sending = false;
      if (submit) submit.disabled = false;
      composer?.focus();
    }
  }

  function schedulePoll() {
    window.clearTimeout(pollTimer);
    pollTimer = window.setTimeout(async () => {
      try {
        await refreshConversations({ includeMessages: state.display === 'open' });
      } catch {
        // Keep the existing UI while a temporary polling request fails.
      } finally {
        schedulePoll();
      }
    }, document.hidden ? POLL_HIDDEN_MS : POLL_VISIBLE_MS);
  }

  document.addEventListener('visibilitychange', schedulePoll);

  window.AITSMChat = {
    mount,
    open,
    close,
    minimize,
    restore,
    refresh: refreshConversations,
    selectConversation,
    acknowledgeRead,
    getConversations: () => [...conversations]
  };

  mount();
  const pendingId = Number(window.__aitsmPendingChatId || 0);
  if (pendingId > 0) open(pendingId);
  delete window.__aitsmPendingChatId;
})();
