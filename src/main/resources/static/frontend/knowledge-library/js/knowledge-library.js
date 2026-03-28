let knowledgeLibraryArticles = [];
function normalizeListResponse(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
}

async function fetchKnowledgeLibrary() {
  const res = await fetch('/api/knowledge', { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Unable to load knowledge articles');
  return normalizeListResponse(data);
}

function filterKnowledgeLibrary(items) {
  const query = (document.getElementById('search')?.value || '').trim().toLowerCase();
  const category = (document.getElementById('knowledgeCategory')?.value || '').trim().toLowerCase();

  return items.filter((article) => {
    const matchesQuery = !query || [article.title, article.content, article.category].some((value) => (value || '').toLowerCase().includes(query));
    const matchesCategory = !category || (article.category || '').toLowerCase() === category;
    return matchesQuery && matchesCategory;
  });
}

function formatKnowledgeLibraryDate(value) {
  const date = new Date(value || '');
  return Number.isNaN(date.getTime()) ? (value || '-') : date.toLocaleString();
}

function ensureKnowledgeLibraryModal() {
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
        <button type='button' class='btn btn-ghost' onclick='closeKnowledgeLibraryModal()'>Close</button>
      </div>
      <div id='knowledgeModalContent' class='small' style='white-space:pre-wrap;max-height:60vh;overflow:auto'></div>
    </div>`;
  document.body.appendChild(modal);
  modal.addEventListener('click', (event) => {
    if (event.target?.id === 'knowledgeArticleModal') closeKnowledgeLibraryModal();
  });
}

function openKnowledgeLibraryModal(id) {
  const article = knowledgeLibraryArticles.find((item) => item.id === id);
  if (!article) return alert('Unable to open the selected article.');
  ensureKnowledgeLibraryModal();
  document.getElementById('knowledgeModalTitle').textContent = article.title || 'Untitled';
  document.getElementById('knowledgeModalMeta').textContent = `${article.category || 'General'} • ${formatKnowledgeLibraryDate(article.createdAt)}`;
  document.getElementById('knowledgeModalContent').textContent = article.content || '';
  const modal = document.getElementById('knowledgeArticleModal');
  modal?.classList.remove('hidden');
  modal?.classList.add('show');
}

function closeKnowledgeLibraryModal() {
  const modal = document.getElementById('knowledgeArticleModal');
  modal?.classList.remove('show');
  modal?.classList.add('hidden');
}

function renderKnowledgeLibrary(items) {
  const host = document.getElementById('knowledgeList');
  if (!host) return;

  if (!items.length) {
    renderTableEmptyState(host, 4, 'No matching articles found.');
    return;
  }

  host.innerHTML = items.map((article) => `
    <tr>
      <td><button type='button' class='btn btn-ghost' onclick='openKnowledgeLibraryModal(${article.id})'>${article.title || 'Untitled'}</button></td>
      <td>${article.category || 'General'}</td>
      <td>${formatKnowledgeLibraryDate(article.createdAt)}</td>
      <td><button type='button' class='btn btn-ghost' onclick='openKnowledgeLibraryModal(${article.id})'>Read</button></td>
    </tr>
  `).join('');
}

async function loadKnowledgeLibrary() {
  const host = document.getElementById('knowledgeList');
  if (host) showTableSkeleton(host, { rowCount: 6, columnCount: 4, hasActions: true });

  try {
    knowledgeLibraryArticles = await fetchKnowledgeLibrary();
    if (host) clearTableSkeleton(host);
    renderKnowledgeLibrary(filterKnowledgeLibrary(knowledgeLibraryArticles));
  } catch (error) {
    if (host) renderTableErrorState(host, 4, error.message);
  } finally {
    if (host) clearTableSkeleton(host);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  ensureKnowledgeLibraryModal();
  loadKnowledgeLibrary();
});
