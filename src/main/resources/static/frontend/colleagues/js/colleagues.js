(function initializeColleagueWorkspace() {
  let activeColleague = null;
  let activeColleagueName = '';
  let searchTimer = null;
  let sending = false;
  let lastRenderedMessageId = null;

  const currentUserId = () => Number(localStorage.getItem('userId') || 0);

  async function api(url, options = {}) {
    const response = await fetch(url, {
      ...options,
      headers: { ...authHeaders(), ...(options.headers || {}) }
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || data.message || 'Request failed.');
    return data;
  }

  function createAvatar(colleague) {
    const avatar = document.createElement('span');
    avatar.className = 'colleague-avatar';
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

  function createEmptyState(title, message, { error = false } = {}) {
    const empty = document.createElement('div');
    empty.className = `colleague-empty-state${error ? ' is-error' : ''}`;
    const heading = document.createElement('strong');
    const copy = document.createElement('span');
    heading.textContent = title;
    copy.textContent = message;
    empty.append(heading, copy);
    return empty;
  }

  function createButton(label, className, handler) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = className;
    button.textContent = label;
    button.addEventListener('click', handler);
    return button;
  }

  function createLink(label, className, href) {
    const link = document.createElement('a');
    link.className = className;
    link.href = href;
    link.textContent = label;
    return link;
  }

  function formatTime(value) {
    if (!value) return '';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  function renderPersonCard(result) {
    const colleague = result.colleague;
    const row = document.createElement('article');
    row.className = 'colleague-row';
    const identity = document.createElement('div');
    identity.className = 'colleague-identity';
    identity.appendChild(createAvatar(colleague));
    const copy = document.createElement('div');
    const name = document.createElement('strong');
    const email = document.createElement('span');
    const detail = document.createElement('span');
    name.textContent = colleague.fullName || 'Colleague';
    email.className = 'small colleague-email';
    email.textContent = colleague.email || '';
    detail.className = 'small';
    detail.textContent = `${colleague.department || 'Department pending'} · ${String(colleague.role || '').replaceAll('_', ' ')}`;
    copy.append(name, email, detail);
    identity.appendChild(copy);

    const actions = document.createElement('div');
    actions.className = 'colleague-actions';
    actions.appendChild(createLink('Profile', 'btn btn-ghost', colleagueProfileUrl(colleague.id)));
    if (result.relationship === 'PENDING') {
      const status = document.createElement('span');
      status.className = 'relationship-note';
      status.textContent = result.requestedByCurrentUser ? 'Request sent' : 'Incoming request';
      actions.appendChild(status);
    } else {
      actions.appendChild(createButton('Add colleague', 'btn btn-primary', () => requestColleague(colleague.id)));
    }
    row.append(identity, actions);
    return row;
  }

  async function loadSearch() {
    const input = document.getElementById('colleagueSearch');
    const results = document.getElementById('searchResults');
    if (!input || !results) return;
    results.replaceChildren(createEmptyState('Searching…', 'Looking for approved colleagues.'));
    try {
      const users = await api(`/api/colleagues/search?q=${encodeURIComponent(input.value.trim())}`);
      const discoverable = (Array.isArray(users) ? users : []).filter((result) => result.relationship !== 'ACCEPTED');
      results.replaceChildren();
      if (!discoverable.length) {
        results.appendChild(createEmptyState('No matching colleagues', 'Try another name, email, department, or role.'));
        return;
      }
      discoverable.forEach((result) => results.appendChild(renderPersonCard(result)));
    } catch (error) {
      results.replaceChildren(createEmptyState('Colleagues unavailable', error.message, { error: true }));
    }
  }

  async function requestColleague(id) {
    try {
      await api(`/api/colleagues/${id}/request`, { method: 'POST' });
      await Promise.all([loadSearch(), loadRequests()]);
    } catch (error) {
      alert(error.message);
    }
  }

  async function loadRequestPerson(row) {
    const otherId = row.recipientId === currentUserId() ? row.senderId : row.recipientId;
    try {
      const profile = await api(`/api/colleagues/${otherId}/profile`);
      return { row, colleague: profile.colleague, otherId };
    } catch {
      return { row, colleague: null, otherId };
    }
  }

  async function loadRequests() {
    const list = document.getElementById('requestList');
    if (!list) return;
    try {
      const rows = await api('/api/colleagues/requests');
      const pending = (Array.isArray(rows) ? rows : []).filter((row) => row.status === 'PENDING');
      const requests = await Promise.all(pending.map(loadRequestPerson));
      list.replaceChildren();
      if (!requests.length) {
        list.appendChild(createEmptyState('No pending requests', 'New requests will appear here.'));
        return;
      }
      requests.forEach(({ row, colleague, otherId }) => {
        const item = document.createElement('article');
        item.className = 'colleague-request-row';
        const copy = document.createElement('div');
        const name = document.createElement('strong');
        const status = document.createElement('span');
        name.textContent = colleague?.fullName || `User #${otherId}`;
        status.className = 'small';
        const incoming = row.recipientId === currentUserId();
        status.textContent = incoming ? 'Wants to connect with you' : 'Waiting for a response';
        copy.append(name, status);
        const actions = document.createElement('div');
        actions.className = 'colleague-actions';
        if (incoming) {
          actions.append(
            createButton('Accept', 'btn btn-primary', () => respondRequest(row.id, true, row.senderId)),
            createButton('Decline', 'btn btn-ghost', () => respondRequest(row.id, false))
          );
        } else {
          actions.appendChild(createButton('Cancel', 'btn btn-ghost', () => cancelRequest(row.id)));
        }
        item.append(copy, actions);
        list.appendChild(item);
      });
    } catch (error) {
      list.replaceChildren(createEmptyState('Requests unavailable', error.message, { error: true }));
    }
  }

  async function respondRequest(id, accepted, senderId = null) {
    try {
      await api(`/api/colleagues/requests/${id}/${accepted ? 'accept' : 'decline'}`, { method: 'POST' });
      await loadAll();
      await window.AITSMChat?.refresh({ includeMessages: false });
      if (accepted && senderId) {
        const profile = await api(`/api/colleagues/${senderId}/profile`);
        await openConversation(senderId, profile.colleague?.fullName || 'Colleague');
      }
    } catch (error) {
      alert(error.message);
    }
  }

  async function cancelRequest(id) {
    try {
      await api(`/api/colleagues/requests/${id}`, { method: 'DELETE' });
      await Promise.all([loadSearch(), loadRequests()]);
    } catch (error) {
      alert(error.message);
    }
  }

  function renderConversations(items) {
    const list = document.getElementById('conversationList');
    if (!list) return;
    list.replaceChildren();
    if (!items.length) {
      list.appendChild(createEmptyState('No conversations yet', 'Your accepted colleagues will appear here.'));
      return;
    }
    items.forEach((item) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `conversation-row${Number(item.colleague?.id) === Number(activeColleague) ? ' is-active' : ''}`;
      button.appendChild(createAvatar(item.colleague));
      const copy = document.createElement('span');
      copy.className = 'conversation-copy';
      const top = document.createElement('span');
      top.className = 'conversation-topline';
      const name = document.createElement('strong');
      const time = document.createElement('small');
      name.textContent = item.colleague?.fullName || 'Colleague';
      time.textContent = formatTime(item.lastMessage?.sentAt);
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
      button.addEventListener('click', () => openConversation(item.colleague.id, item.colleague.fullName));
      list.appendChild(button);
    });
  }

  async function loadConversations() {
    try {
      const items = await api('/api/colleagues/messages/conversations');
      renderConversations(Array.isArray(items) ? items : []);
      return items;
    } catch (error) {
      document.getElementById('conversationList')?.replaceChildren(createEmptyState('Conversations unavailable', error.message, { error: true }));
      return [];
    }
  }

  function selectMobilePanel(name) {
    document.querySelectorAll('[data-colleague-panel]').forEach((panel) => panel.classList.toggle('is-mobile-active', panel.dataset.colleaguePanel === name));
    document.querySelectorAll('[data-colleague-tab]').forEach((tab) => {
      const active = tab.dataset.colleagueTab === name;
      tab.classList.toggle('is-active', active);
      tab.setAttribute('aria-selected', String(active));
    });
  }

  function renderActiveHeader(id, name) {
    const header = document.getElementById('chatTitle');
    if (!header) return;
    header.replaceChildren();
    const identity = document.createElement('div');
    const title = document.createElement('h2');
    const detail = document.createElement('p');
    title.className = 'section-title';
    title.textContent = name || 'Colleague';
    detail.className = 'section-subtitle';
    detail.textContent = 'Connected colleague';
    identity.append(title, detail);
    const profile = createLink('Profile', 'btn btn-ghost', colleagueProfileUrl(id));
    header.append(identity, profile);
  }

  async function openConversation(id, name) {
    activeColleague = Number(id);
    activeColleagueName = name || 'Colleague';
    lastRenderedMessageId = null;
    renderActiveHeader(activeColleague, activeColleagueName);
    document.getElementById('messageForm')?.classList.remove('hidden');
    selectMobilePanel('messages');
    const known = window.AITSMChat?.getConversations?.() || [];
    if (known.length) renderConversations(known);
    await loadMessages();
  }

  function renderMessages(messages) {
    const list = document.getElementById('messageList');
    if (!list) return;
    const nearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 72;
    const firstRender = lastRenderedMessageId === null;
    list.replaceChildren();
    if (!messages.length) {
      list.appendChild(createEmptyState('No messages yet', 'Say hello to start the conversation.'));
    } else {
      messages.forEach((message) => {
        const bubble = document.createElement('div');
        bubble.className = `message${Number(message.senderId) === currentUserId() ? ' mine' : ''}`;
        const body = document.createElement('p');
        const time = document.createElement('time');
        body.textContent = message.body || '';
        time.textContent = formatTime(message.sentAt);
        time.dateTime = message.sentAt || '';
        bubble.append(body, time);
        list.appendChild(bubble);
      });
    }
    if (firstRender || nearBottom) requestAnimationFrame(() => { list.scrollTop = list.scrollHeight; });
    lastRenderedMessageId = messages.at(-1)?.id || null;
  }

  async function loadMessages() {
    if (!activeColleague) return;
    try {
      const messages = await api(`/api/colleagues/${activeColleague}/messages`);
      renderMessages(Array.isArray(messages) ? messages : []);
      window.AITSMChat?.acknowledgeRead(activeColleague);
      await api(`/api/notifications/direct-messages/${activeColleague}/read`, { method: 'PATCH' }).catch(() => null);
      window.loadUserHeaderNotifications?.();
      window.loadHeaderNotifications?.();
    } catch (error) {
      document.getElementById('messageList')?.replaceChildren(createEmptyState('Messages unavailable', error.message, { error: true }));
    }
  }

  async function sendMessage(event) {
    event.preventDefault();
    if (!activeColleague || sending) return;
    const input = document.getElementById('messageBody');
    const button = event.currentTarget.querySelector('button[type="submit"]');
    const status = document.getElementById('messageSendStatus');
    const body = input?.value.trim() || '';
    if (!body) return;
    sending = true;
    if (button) button.disabled = true;
    if (status) status.textContent = 'Sending…';
    try {
      await api(`/api/colleagues/${activeColleague}/messages`, { method: 'POST', body: JSON.stringify({ body }) });
      if (input) input.value = '';
      if (status) status.textContent = '';
      await Promise.all([loadMessages(), window.AITSMChat?.refresh({ includeMessages: false }) || loadConversations()]);
    } catch (error) {
      if (status) status.textContent = error.message || 'Message failed to send. Try again.';
    } finally {
      sending = false;
      if (button) button.disabled = false;
      input?.focus();
    }
  }

  async function loadAll() {
    await Promise.all([loadSearch(), loadRequests(), loadConversations()]);
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-colleague-tab]').forEach((tab) => tab.addEventListener('click', () => selectMobilePanel(tab.dataset.colleagueTab)));
    document.getElementById('colleagueSearch')?.addEventListener('input', () => {
      window.clearTimeout(searchTimer);
      searchTimer = window.setTimeout(loadSearch, 220);
    });
    document.getElementById('messageForm')?.addEventListener('submit', sendMessage);

    document.addEventListener('aitsm:chat-conversations', (event) => {
      const items = Array.isArray(event.detail?.conversations) ? event.detail.conversations : [];
      renderConversations(items);
      if (activeColleague && !document.hidden) loadMessages();
    });

    loadAll().then(async () => {
      const chatId = Number(new URLSearchParams(location.search).get('chat') || 0);
      if (chatId > 0) {
        const profile = await api(`/api/colleagues/${chatId}/profile`);
        if (profile.relationship !== 'ACCEPTED') throw new Error('You can only message accepted colleagues.');
        await openConversation(chatId, profile.colleague?.fullName || 'Colleague');
      }
    }).catch((error) => alert(error.message));
  });
})();
