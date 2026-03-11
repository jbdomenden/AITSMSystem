const headers = window.headers || (() => ({ 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '', 'X-User-Role': localStorage.getItem('role') || '' }));
async function loadNotifications(){ return fetch('/api/notifications',{headers:headers()}).then(r=>r.json()); }
