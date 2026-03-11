const headers = window.headers || (() => ({ 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '', 'X-User-Role': localStorage.getItem('role') || '' }));
async function loadMonitoring(){
  const el = document.getElementById('monitorCards'); if(!el) return;
  const devices = await fetch('/api/monitoring/devices').then(r=>r.json());
  el.innerHTML = devices.map(d=>`<div class='card'><h3>${d.deviceName}</h3><small>${d.ipAddress}</small><p>CPU: ${d.cpuUsage}% | Memory: ${d.memoryUsage}%</p><span class='badge'>${d.status}</span></div>`).join('');
}

async function registerDevice(){
  const body = { deviceName:deviceName.value, ipAddress:ipAddress.value, department:department.value, assignedUser:assignedUser.value, status:status.value };
  const res = await fetch('/api/devices', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
  const data = await res.json();
  if(!res.ok) return alert(data.error || 'Registration failed (LAN-only policy enforced).');
  loadDevices();
}

async function loadDevices(){
  const el = document.getElementById('deviceList'); if(!el) return;
  const devices = await fetch('/api/devices').then(r=>r.json());
  el.innerHTML = devices.map(d=>`<li>${d.deviceName} (${d.ipAddress}) - ${d.status}</li>`).join('');
}
document.addEventListener('DOMContentLoaded', ()=>{loadMonitoring(); loadDevices();});
