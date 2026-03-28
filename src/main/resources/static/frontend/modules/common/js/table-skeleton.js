(function tableSkeletonModule(global) {
  function resolveBody(target) {
    if (!target) return null;
    if (target.tagName && target.tagName.toLowerCase() === 'tbody') return target;
    return target.querySelector ? target.querySelector('tbody') : null;
  }

  function setBusyState(body, isBusy) {
    if (!body) return;
    body.setAttribute('aria-busy', isBusy ? 'true' : 'false');
    const table = body.closest('table');
    if (table) table.setAttribute('aria-busy', isBusy ? 'true' : 'false');
  }

  function showTableSkeleton(target, options) {
    const body = resolveBody(target);
    if (!body) return;

    const cfg = {
      rowCount: 6,
      columnCount: 1,
      hasCheckbox: false,
      hasActions: false,
      ...(options || {})
    };

    const totalColumns = Math.max(1, Number(cfg.columnCount) || 1);
    const rowCount = Math.max(1, Number(cfg.rowCount) || 1);
    const cells = Array.from({ length: totalColumns }).map((_, idx) => {
      const classes = ['table-skeleton-cell'];
      if (cfg.hasCheckbox && idx === 0) classes.push('table-skeleton-cell-checkbox');
      if (cfg.hasActions && idx === totalColumns - 1) classes.push('table-skeleton-cell-actions');
      return `<td class='${classes.join(' ')}'><span class='table-skeleton-block' aria-hidden='true'></span></td>`;
    }).join('');

    body.innerHTML = Array.from({ length: rowCount }).map(() => `<tr class='table-skeleton-row' aria-hidden='true'>${cells}</tr>`).join('');
    setBusyState(body, true);
  }

  function clearTableSkeleton(target) {
    const body = resolveBody(target);
    if (!body) return;
    body.querySelectorAll('.table-skeleton-row').forEach((row) => row.remove());
    setBusyState(body, false);
  }

  function renderTableEmptyState(target, columnCount, message) {
    const body = resolveBody(target);
    if (!body) return;
    clearTableSkeleton(body);
    body.innerHTML = `<tr><td colspan='${Math.max(1, Number(columnCount) || 1)}' class='small'>${message || 'No records found.'}</td></tr>`;
  }

  function renderTableErrorState(target, columnCount, message) {
    const body = resolveBody(target);
    if (!body) return;
    clearTableSkeleton(body);
    body.innerHTML = `<tr><td colspan='${Math.max(1, Number(columnCount) || 1)}' class='small text-danger'>${message || 'Unable to load table data.'}</td></tr>`;
  }

  global.showTableSkeleton = showTableSkeleton;
  global.clearTableSkeleton = clearTableSkeleton;
  global.renderTableEmptyState = renderTableEmptyState;
  global.renderTableErrorState = renderTableErrorState;
})(window);
