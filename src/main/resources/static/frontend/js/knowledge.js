async function fetchKnowledge() {
  const res = await fetch('/api/knowledge', { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Unable to load knowledge articles');
  return Array.isArray(data) ? data : [];
}

function filterKnowledge(items) {
  const query = (document.getElementById('search')?.value || '').trim().toLowerCase();
  const category = (document.getElementById('knowledgeCategory')?.value || '').trim().toLowerCase();

  return items.filter(a => {
    const matchesQuery = !query || [a.title, a.content, a.category].some(v => (v || '').toLowerCase().includes(query));
    const matchesCategory = !category || (a.category || '').toLowerCase() === category;
    return matchesQuery && matchesCategory;
  });
}

function renderKnowledge(items) {
  const host = document.getElementById('knowledgeCards');
  if (!host) return;

  if (!items.length) {
    host.innerHTML = "<div class='empty-state'><h3>No matching articles</h3><p>Try another search term or create a new article.</p></div>";
    return;
  }

  host.innerHTML = items.map(a => `
    <article class='card'>
      <div class='card-head'>
        <div>
          <h3 class='section-title'>${a.title || 'Untitled'}</h3>
          <p class='section-subtitle'>${a.category || 'General'} • ${new Date(a.createdAt).toLocaleString()}</p>
        </div>
      </div>
      <p class='small' style='white-space:pre-wrap'>${a.content || ''}</p>
    </article>
  `).join('');
}

async function loadKnowledge() {
  const host = document.getElementById('knowledgeCards');
  if (host) host.innerHTML = "<p class='small'>Loading articles...</p>";

  try {
    const articles = await fetchKnowledge();
    renderKnowledge(filterKnowledge(articles));
  } catch (error) {
    if (host) host.innerHTML = `<p class='small text-danger'>${error.message}</p>`;
  }
}

async function createArticle() {
  const body = {
    title: (document.getElementById('articleTitle')?.value || '').trim(),
    category: (document.getElementById('articleCategory')?.value || 'General').trim(),
    content: (document.getElementById('articleContent')?.value || '').trim()
  };

  if (!body.title || !body.content) return alert('Title and content are required.');

  const res = await fetch('/api/knowledge', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to create article');

  document.getElementById('articleTitle').value = '';
  document.getElementById('articleCategory').value = '';
  document.getElementById('articleContent').value = '';
  await loadKnowledge();
}

document.addEventListener('DOMContentLoaded', loadKnowledge);
