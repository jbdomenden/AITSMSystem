function initHelpTips(){
  document.querySelectorAll('.help-tip[data-help]').forEach((el)=>{
    el.setAttribute('title', el.getAttribute('data-help') || 'Help');
    el.setAttribute('aria-label', el.getAttribute('data-help') || 'Help');
  });
}
document.addEventListener('DOMContentLoaded', initHelpTips);
