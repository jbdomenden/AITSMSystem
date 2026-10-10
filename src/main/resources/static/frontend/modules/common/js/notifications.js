const headers = window.headers || authHeaders;
async function loadNotifications(){ return fetch('/api/notifications',{headers:headers()}).then(r=>r.json()); }
