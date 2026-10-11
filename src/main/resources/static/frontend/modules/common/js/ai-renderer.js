(function () {
  'use strict';

  function normalize(source) {
    return String(source ?? '')
      .replace(/\r\n?/g, '\n')
      .replace(/^\s*```[a-z0-9_-]*\s*$/gim, '')
      .replace(/^\s*#{1,6}\s*/gm, '')
      .replace(/^\s*(?:---+|\*\*\*+)\s*$/gm, '')
      .replace(/\*\*([^*\n]+)\*\*/g, '$1')
      .replace(/__([^_\n]+)__/g, '$1')
      .replace(/`([^`\n]+)`/g, '$1')
      .replace(/[ \t]+$/gm, '')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  }

  function appendLinkedText(parent, text) {
    const pattern = /https?:\/\/[^\s<>"']+/gi;
    let index = 0;
    for (const match of text.matchAll(pattern)) {
      parent.append(document.createTextNode(text.slice(index, match.index)));
      const candidate = match[0];
      try {
        const url = new URL(candidate);
        if (!['http:', 'https:'].includes(url.protocol)) throw new Error('unsafe');
        const link = document.createElement('a');
        link.href = url.href;
        link.textContent = candidate;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        parent.append(link);
      } catch {
        parent.append(document.createTextNode(candidate));
      }
      index = match.index + candidate.length;
    }
    parent.append(document.createTextNode(text.slice(index)));
  }

  function render(container, source) {
    container.replaceChildren();
    const lines = normalize(source).split('\n');
    let list = null;
    let listType = '';
    const flushList = () => { list = null; listType = ''; };
    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line) { flushList(); continue; }
      const ordered = line.match(/^\d+[.)]\s+(.+)$/);
      const bullet = line.match(/^[-*+]\s+(.+)$/);
      if (ordered || bullet) {
        const type = ordered ? 'ol' : 'ul';
        if (!list || listType !== type) {
          list = document.createElement(type);
          listType = type;
          container.append(list);
        }
        const item = document.createElement('li');
        appendLinkedText(item, (ordered || bullet)[1]);
        list.append(item);
        continue;
      }
      flushList();
      const isHeading = /^(likely causes|troubleshooting steps|expected result|next step|escalation guidance|issue summary|suggested priority|troubleshooting actions):?$/i.test(line);
      const node = document.createElement(isHeading ? 'h4' : 'p');
      appendLinkedText(node, line.replace(/:$/, isHeading ? '' : ':'));
      container.append(node);
    }
    if (!container.childNodes.length) {
      const empty = document.createElement('p');
      empty.textContent = 'No troubleshooting guidance was returned.';
      container.append(empty);
    }
    return container;
  }

  window.AitsmAiRenderer = { normalize, render };
})();
