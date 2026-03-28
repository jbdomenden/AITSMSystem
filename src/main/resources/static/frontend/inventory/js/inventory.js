const inventoryState = {
  page: 1,
  pageSize: 10,
  total: 0,
  selectedId: null,
  loading: false
};

function inventoryStatusBadge(status) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'online') return 'resolved';
  if (normalized === 'offline') return 'warning';
  if (normalized === 'stale') return 'open';
  return 'in-progress';
}

function inventoryField(label, value) {
  return `<div class='inventory-field'><div class='inventory-field-label'>${label}</div><div>${value || 'Unavailable'}</div></div>`;
}

function parseListResponse(payload) {
  if (Array.isArray(payload)) return { data: payload, meta: { totalCount: payload.length, pageSize: payload.length, currentPage: 1 } };
  if (Array.isArray(payload?.data)) return payload;
  return { data: [], meta: { totalCount: 0, pageSize: inventoryState.pageSize, currentPage: inventoryState.page } };
}

function buildInventoryQuery() {
  const params = new URLSearchParams();
  const search = (document.getElementById('inventorySearch')?.value || '').trim();
  const status = (document.getElementById('inventoryStatusFilter')?.value || '').trim();
  const department = (document.getElementById('inventoryDepartmentFilter')?.value || '').trim();
  const source = (document.getElementById('inventorySourceFilter')?.value || '').trim();

  if (search) params.set('search', search);
  if (status) params.set('status', status);
  if (department) params.set('department', department);
  if (source) params.set('connectionSource', source);

  params.set('page', String(inventoryState.page));
  params.set('pageSize', String(inventoryState.pageSize));
  params.set('sortBy', 'lastSeen');
  params.set('sortOrder', 'desc');

  return params.toString();
}

function renderInventoryStats(stats) {
  const host = document.getElementById('inventoryStats');
  if (!host) return;

  const cards = [
    ['Total Assets', stats?.total ?? 0],
    ['Online', stats?.online ?? 0],
    ['Offline', stats?.offline ?? 0],
    ['Stale', stats?.stale ?? 0]
  ];

  host.innerHTML = cards.map(([label, value]) => `<article class='card metric-card'><div class='metric-label'>${label}</div><div class='metric-value'>${value}</div></article>`).join('');
}

function updateInventoryPagination(meta) {
  const label = document.getElementById('inventoryPageLabel');
  const prev = document.getElementById('inventoryPrevBtn');
  const next = document.getElementById('inventoryNextBtn');

  inventoryState.total = Number(meta?.totalCount || 0);
  const currentPage = Number(meta?.currentPage || inventoryState.page || 1);
  const pageSize = Number(meta?.pageSize || inventoryState.pageSize || 10);
  const totalPages = Math.max(1, Math.ceil(inventoryState.total / pageSize));

  if (label) label.textContent = `Page ${currentPage} of ${totalPages}`;
  if (prev) prev.disabled = currentPage <= 1 || inventoryState.loading;
  if (next) next.disabled = currentPage >= totalPages || inventoryState.loading;
}

function renderInventoryRows(items) {
  const rows = document.getElementById('inventoryRows');
  if (!rows) return;

  if (!items.length) {
    renderTableEmptyState(rows, 12, 'No registered assets found for the selected filters.');
    return;
  }

  rows.innerHTML = items.map((asset) => `
    <tr>
      <td>${asset.deviceName || asset.hostname || 'Unavailable'}</td>
      <td>${asset.ipAddress || 'Unavailable'}</td>
      <td>${asset.manufacturer || 'Unavailable'}</td>
      <td>${asset.model || 'Unavailable'}</td>
      <td>${asset.processorName || 'Unavailable'}</td>
      <td>${asset.installedRam || 'Unavailable'}</td>
      <td>${asset.storageSummary || 'Unavailable'}</td>
      <td>${asset.osSummary || 'Unavailable'}</td>
      <td><span class='badge ${inventoryStatusBadge(asset.status)}'>${asset.status || 'unknown'}</span></td>
      <td>${asset.lastSeenAt || 'Unavailable'}</td>
      <td>${asset.assignedDepartment || 'Unavailable'}</td>
      <td><button class='btn btn-ghost' type='button' onclick='openInventoryDetail(${asset.id})'>View</button></td>
    </tr>
  `).join('');
}

async function loadInventory() {
  const rows = document.getElementById('inventoryRows');
  if (!rows || inventoryState.loading) return;

  inventoryState.loading = true;
  showTableSkeleton(rows, { rowCount: 8, columnCount: 12, hasActions: true });

  try {
    const query = buildInventoryQuery();
    const [listRes, statsRes] = await Promise.all([
      fetch(`/api/inventory?${query}`, { headers: authHeaders() }),
      fetch('/api/inventory/stats', { headers: authHeaders() })
    ]);

    const listPayload = await listRes.json();
    const statsPayload = await statsRes.json();

    if (!listRes.ok) {
      renderTableErrorState(rows, 12, listPayload.error || 'Unable to load inventory assets.');
      return;
    }

    const normalized = parseListResponse(listPayload);
    clearTableSkeleton(rows);
    renderInventoryRows(normalized.data || []);
    updateInventoryPagination(normalized.meta);
    renderInventoryStats(statsRes.ok ? statsPayload : null);
  } catch (error) {
    renderTableErrorState(rows, 12, error.message || 'Unable to load inventory assets.');
  } finally {
    clearTableSkeleton(rows);
    inventoryState.loading = false;
    updateInventoryPagination({ totalCount: inventoryState.total, pageSize: inventoryState.pageSize, currentPage: inventoryState.page });
  }
}

function renderInventorySummaryCards(asset) {
  const host = document.getElementById('inventoryDetailSummary');
  if (!host) return;
  host.innerHTML = [
    ['Processor', asset.processorName || 'Unavailable'],
    ['RAM', asset.installedRam || 'Unavailable'],
    ['Storage Free', asset.storageFree || 'Unavailable'],
    ['GPU', asset.gpuName || 'Unavailable']
  ].map(([label, value]) => `<article class='card metric-card'><div class='metric-label'>${label}</div><div class='metric-hint'>${value}</div></article>`).join('');
}

function renderInventoryDetailGroups(asset) {
  const setFields = (id, fields) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.innerHTML = fields.join('');
  };

  setFields('inventoryOverviewFields', [
    inventoryField('Device Name', asset.deviceName),
    inventoryField('Hostname', asset.hostname),
    inventoryField('Full Device Name', asset.fullDeviceName),
    inventoryField('Status', `<span class='badge ${inventoryStatusBadge(asset.status)}'>${asset.status || 'unknown'}</span>`),
    inventoryField('Last Seen', asset.lastSeenAt),
    inventoryField('Connection Source', asset.connectionSource)
  ]);

  setFields('inventoryHardwareFields', [
    inventoryField('Manufacturer', asset.manufacturer),
    inventoryField('Model', asset.model),
    inventoryField('System Type', asset.systemType),
    inventoryField('Processor Name', asset.processorName),
    inventoryField('Processor Speed', asset.processorSpeed),
    inventoryField('Installed RAM', asset.installedRam),
    inventoryField('Usable RAM', asset.usableRam),
    inventoryField('RAM Speed', asset.ramSpeed)
  ]);

  setFields('inventoryStorageFields', [
    inventoryField('Storage Total', asset.storageTotal),
    inventoryField('Storage Used', asset.storageUsed),
    inventoryField('Storage Free', asset.storageFree),
    inventoryField('Drive Breakdown', asset.storageBreakdownJson)
  ]);

  setFields('inventoryGraphicsFields', [
    inventoryField('GPU Name', asset.gpuName),
    inventoryField('GPU Memory', asset.gpuMemory),
    inventoryField('Pen & Touch', asset.penTouchSupport)
  ]);

  setFields('inventoryOsFields', [
    inventoryField('OS Name', asset.osName),
    inventoryField('OS Edition', asset.osEdition),
    inventoryField('OS Version', asset.osVersion),
    inventoryField('OS Build', asset.osBuild),
    inventoryField('Installed On', asset.installedOn),
    inventoryField('Domain / Workgroup', asset.domainOrWorkgroup)
  ]);

  setFields('inventoryNetworkFields', [
    inventoryField('IP Address', asset.ipAddress),
    inventoryField('MAC Address', asset.macAddress),
    inventoryField('Device UUID', asset.deviceUuid),
    inventoryField('Product ID', asset.productId),
    inventoryField('Department', asset.assignedDepartment),
    inventoryField('Assigned User', asset.assignedUser),
    inventoryField('Created At', asset.createdAt),
    inventoryField('Updated At', asset.updatedAt)
  ]);
}

async function openInventoryDetail(id) {
  const modal = document.getElementById('inventoryDetailModal');
  if (!modal) return;

  const title = document.getElementById('inventoryDetailTitle');
  const subtitle = document.getElementById('inventoryDetailSubtitle');
  const notesInput = document.getElementById('inventoryNotesInput');

  inventoryState.selectedId = id;
  if (title) title.textContent = 'Loading asset details...';
  if (subtitle) subtitle.textContent = '';
  if (notesInput) notesInput.value = '';

  modal.classList.remove('hidden');
  modal.classList.add('show');

  try {
    const res = await fetch(`/api/inventory/${id}`, { headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Unable to load asset details');

    if (title) title.textContent = data.deviceName || data.hostname || `Asset #${id}`;
    if (subtitle) subtitle.textContent = `${data.ipAddress || 'Unavailable'} • ${data.status || 'unknown'}`;
    if (notesInput) notesInput.value = data.notes || '';

    renderInventorySummaryCards(data);
    renderInventoryDetailGroups(data);
  } catch (error) {
    if (title) title.textContent = 'Asset details unavailable';
    if (subtitle) subtitle.textContent = error.message;
  }
}

function closeInventoryDetail() {
  const modal = document.getElementById('inventoryDetailModal');
  modal?.classList.remove('show');
  modal?.classList.add('hidden');
  inventoryState.selectedId = null;
}

async function saveInventoryNotes() {
  if (!inventoryState.selectedId) return;
  const notes = document.getElementById('inventoryNotesInput')?.value || '';

  const res = await fetch(`/api/inventory/${inventoryState.selectedId}/notes`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ notes })
  });

  const data = await res.json();
  if (!res.ok) {
    alert(data.error || 'Unable to save notes');
    return;
  }

  alert('Notes saved');
  await loadInventory();
}

function bindInventoryEvents() {
  document.getElementById('inventoryRefreshBtn')?.addEventListener('click', () => loadInventory());

  ['inventorySearch', 'inventoryStatusFilter', 'inventoryDepartmentFilter', 'inventorySourceFilter'].forEach((id) => {
    document.getElementById(id)?.addEventListener('input', () => {
      inventoryState.page = 1;
      loadInventory();
    });
    document.getElementById(id)?.addEventListener('change', () => {
      inventoryState.page = 1;
      loadInventory();
    });
  });

  document.getElementById('inventoryPrevBtn')?.addEventListener('click', () => {
    if (inventoryState.page <= 1) return;
    inventoryState.page -= 1;
    loadInventory();
  });

  document.getElementById('inventoryNextBtn')?.addEventListener('click', () => {
    const totalPages = Math.max(1, Math.ceil(inventoryState.total / inventoryState.pageSize));
    if (inventoryState.page >= totalPages) return;
    inventoryState.page += 1;
    loadInventory();
  });

  document.getElementById('inventorySaveNotesBtn')?.addEventListener('click', saveInventoryNotes);
  document.getElementById('inventoryDetailModal')?.addEventListener('click', (event) => {
    if (event.target?.id === 'inventoryDetailModal') closeInventoryDetail();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  bindInventoryEvents();
  loadInventory();
});
