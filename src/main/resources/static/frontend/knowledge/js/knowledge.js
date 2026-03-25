let knowledgeArticles = [];
let editingArticleId = null;

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

function formatKnowledgeDate(value) {
  const date = new Date(value || '');
  return Number.isNaN(date.getTime()) ? (value || '-') : date.toLocaleString();
}

function ensureKnowledgeModal() {
  if (document.getElementById('knowledgeArticleModal')) return;
  const modal = document.createElement('div');
  modal.id = 'knowledgeArticleModal';
  modal.className = 'modal-overlay hidden';
  modal.innerHTML = `
    <div class='modal-card' style='max-width:760px'>
      <div class='card-head'>
        <div>
          <h3 id='knowledgeModalTitle' class='section-title'>Knowledge Article</h3>
          <p id='knowledgeModalMeta' class='section-subtitle'></p>
        </div>
        <button type='button' class='btn btn-ghost' onclick='closeKnowledgeModal()'>Close</button>
      </div>
      <div id='knowledgeModalContent' class='small' style='white-space:pre-wrap;max-height:60vh;overflow:auto'></div>
    </div>`;
  document.body.appendChild(modal);
  modal.addEventListener('click', (event) => {
    if (event.target?.id === 'knowledgeArticleModal') closeKnowledgeModal();
  });
}

function openKnowledgeModal(id) {
  const article = knowledgeArticles.find(item => item.id === id);
  if (!article) return alert('Unable to open the selected article.');
  ensureKnowledgeModal();
  document.getElementById('knowledgeModalTitle').textContent = article.title || 'Untitled';
  document.getElementById('knowledgeModalMeta').textContent = `${article.category || 'General'} • ${formatKnowledgeDate(article.createdAt)}`;
  document.getElementById('knowledgeModalContent').textContent = article.content || '';
  const modal = document.getElementById('knowledgeArticleModal');
  modal?.classList.remove('hidden');
  modal?.classList.add('show');
}

function closeKnowledgeModal() {
  const modal = document.getElementById('knowledgeArticleModal');
  modal?.classList.remove('show');
  modal?.classList.add('hidden');
}

function renderKnowledge(items) {
  const host = document.getElementById('knowledgeList');
  if (!host) return;

  if (!items.length) {
    host.innerHTML = `<tr><td colspan='4' class='small'>No matching articles found.</td></tr>`;
    return;
  }

  host.innerHTML = items.map(a => `
    <tr>
      <td><button type='button' class='btn btn-ghost' onclick='openKnowledgeModal(${a.id})'>${a.title || 'Untitled'}</button></td>
      <td>${a.category || 'General'}</td>
      <td>${formatKnowledgeDate(a.createdAt)}</td>
      <td>
        <div class='table-actions'>
          <button class='btn btn-ghost' type='button' onclick='startKnowledgeEdit(${a.id})'>Edit</button>
          <button class='btn btn-ghost text-danger' type='button' onclick='deleteArticle(${a.id})'>Delete</button>
        </div>
      </td>
    </tr>
  `).join('');
}

async function loadKnowledge() {
  const host = document.getElementById('knowledgeList');
  if (host) host.innerHTML = `<tr><td colspan='4' class='small'>Loading articles...</td></tr>`;

  try {
    knowledgeArticles = await fetchKnowledge();
    renderKnowledge(filterKnowledge(knowledgeArticles));
  } catch (error) {
    if (host) host.innerHTML = `<tr><td colspan='4' class='small text-danger'>${error.message}</td></tr>`;
  }
}

function resetKnowledgeForm() {
  editingArticleId = null;
  document.getElementById('knowledgeFormTitle').textContent = 'Create Article';
  document.getElementById('knowledgeSaveBtn').textContent = 'Publish Article';
  document.getElementById('knowledgeCancelBtn').classList.add('hidden');
  document.getElementById('articleTitle').value = '';
  document.getElementById('articleCategory').value = '';
  document.getElementById('articleContent').value = '';
}

function startKnowledgeEdit(id) {
  const article = knowledgeArticles.find(item => item.id === id);
  if (!article) return alert('Unable to load the selected article.');
  editingArticleId = id;
  document.getElementById('knowledgeFormTitle').textContent = 'Edit Article';
  document.getElementById('knowledgeSaveBtn').textContent = 'Save Changes';
  document.getElementById('knowledgeCancelBtn').classList.remove('hidden');
  document.getElementById('articleTitle').value = article.title || '';
  document.getElementById('articleCategory').value = article.category || '';
  document.getElementById('articleContent').value = article.content || '';
  window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
}

async function saveArticle() {
  const body = {
    title: (document.getElementById('articleTitle')?.value || '').trim(),
    category: (document.getElementById('articleCategory')?.value || 'General').trim(),
    content: (document.getElementById('articleContent')?.value || '').trim()
  };

  if (!body.title || !body.content) return alert('Title and content are required.');

  const endpoint = editingArticleId ? `/api/knowledge/${editingArticleId}` : '/api/knowledge';
  const method = editingArticleId ? 'PUT' : 'POST';
  const res = await fetch(endpoint, {
    method,
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to save article');

  const wasEditing = Boolean(editingArticleId);
  resetKnowledgeForm();
  await loadKnowledge();
  alert(wasEditing ? 'Article updated successfully.' : 'Article published successfully.');
}

async function deleteArticle(id) {
  const article = knowledgeArticles.find(item => item.id === id);
  if (!window.confirm(`Delete article "${article?.title || `#${id}`}"?`)) return;

  const res = await fetch(`/api/knowledge/${id}`, { method: 'DELETE', headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to delete article');

  if (editingArticleId === id) resetKnowledgeForm();
  closeKnowledgeModal();
  await loadKnowledge();
  alert(data.message || 'Article deleted');
}

document.addEventListener('DOMContentLoaded', () => {
  ensureKnowledgeModal();
  loadKnowledge();
});
