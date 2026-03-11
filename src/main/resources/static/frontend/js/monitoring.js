async function loadMonitoring() {
  const wrap = document.getElementById('monitorCards');
  if (!wrap) return;
  const devices = await fetch('/api/monitoring/devices').then(r => r.json());
  wrap.innerHTML = devices.map(d => `
    <div class='card'>
      <h3>${d.deviceName}</h3>
      <div class='small'>${d.ipAddress} • ${d.department}</div>
      <p>CPU ${d.cpuUsage}% | Memory ${d.memoryUsage}%</p>
      <span class='badge ${d.status.toLowerCase()==='critical'?'open':'resolved'}'>${d.status}</span>
    </div>`).join('') || `<div class='card'>No LAN devices found.</div>`;
}

async function registerDevice() {
  const body = {
    deviceName: deviceName.value.trim(),
    ipAddress: ipAddress.value.trim(),
    department: department.value.trim(),
    assignedUser: assignedUser.value.trim(),
    status: status.value
  };
  const res = await fetch('/api/devices', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Failed to register device (LAN only policy).');
  deviceName.value = ipAddress.value = department.value = assignedUser.value = '';
  await loadDevices();
}

async function loadDevices() {
  const list = document.getElementById('deviceList');
  if (!list) return;
  const devices = await fetch('/api/devices').then(r => r.json());
  list.innerHTML = devices.map(d => `<li>${d.deviceName} (${d.ipAddress}) - ${d.assignedUser}</li>`).join('');
}

document.addEventListener('DOMContentLoaded', () => { loadMonitoring(); loadDevices(); });
