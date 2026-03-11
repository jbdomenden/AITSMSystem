async function suggestFixes(){
  const tips = document.getElementById('aiTips');
  const res = await fetch('/api/ai/troubleshoot', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({ description: document.getElementById('description')?.value || '' })});
  const data = await res.json();
  tips.innerHTML = data.suggestions.map(s=>`<li>${s}</li>`).join('');
}
