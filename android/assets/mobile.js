(function () {
  if (window.magpieAndroidInstalled) return;
  window.magpieAndroidInstalled = true;
  // Invoke the upstream editor, including its own dirty-state and loading rules.
  window.magpieAndroidProvider = function (id, add) {
    if (add && typeof addProviderFromAgents === 'function') { addProviderFromAgents(); return true; }
    if (!add && typeof openProvider === 'function' && typeof id === 'string') { openProvider(id); return true; }
    return false;
  };
  // Groups have a page of their own; never route an edit through request Routing.
  // A false result keeps the native intent pending while the real list loads.
  window.magpieAndroidGroup = function (id, add) {
    const root=document.getElementById('view-groups');
    if(!root||root.hidden)return false;
    const target=add?root.querySelector('.rt-gnew'):
      Array.from(root.querySelectorAll('.rt-group')).find(row=>row.dataset.id===id);
    if(!target)return false;
    target.click();return true;
  };
  document.documentElement.classList.add('magpie-android');
  document.body.classList.add('magpie-android');
  window.magpieAndroidVisualOptions = (solid, still) => {
    document.body.classList.toggle('android-solid', solid);
    document.body.classList.toggle('android-still', still);
  };
  let announced = '', announcedBridge;
  window.magpieAndroidReportView = () => {
    const current = document.querySelector('.view[id^="view-"]:not([hidden])');
    const bridge = window.AndroidExport;
    if (!current || !bridge?.view) return;
    const id = current.id.slice(5);
    if (!id || (id === announced && bridge === announcedBridge)) return;
    announced = id;
    announcedBridge = bridge;
    bridge.view(id);
  };
  new MutationObserver(window.magpieAndroidReportView).observe(document.body, {
    childList: true, subtree: true, attributes: true, attributeFilter: ['hidden']
  });
  window.magpieAndroidReportView();
  window.magpieAndroidPluginTab = tab => {
    if (tab !== 'builtin' && tab !== 'installed') return;
    window.pluginTabTo?.(tab);
    window.loadPlugins?.();
  };
  // Upstream seals backups on its own filesystem. On the local phone the
  // native host copies only the newly named encrypted backup to SAF.
  const originalFetch = window.fetch.bind(window);
  let pluginEpoch=0, pluginPending=false, operationPanel=null;
  function mountPluginOperation(){
    const root=document.getElementById('view-plugins');
    if(root&&operationPanel&&!operationPanel.isConnected)root.prepend(operationPanel);
  }
  new MutationObserver(mountPluginOperation).observe(document.body,{childList:true,subtree:true});
  function pluginStatus(epoch,message,detail,retry){
    if(epoch!==pluginEpoch)return;
    if(!operationPanel){operationPanel=document.createElement('section');operationPanel.id='android-plugin-operation';operationPanel.setAttribute('aria-live','polite');}
    operationPanel.classList.toggle('pending',pluginPending);operationPanel.setAttribute('role',retry?'alert':'status');operationPanel.replaceChildren();
    const title=document.createElement('div');title.className='android-plugin-title';title.textContent=message;operationPanel.append(title);
    if(detail){const text=document.createElement('p');text.textContent=detail;operationPanel.append(text);}
    if(retry){const button=document.createElement('button');button.type='button';button.textContent='重试安装';button.onclick=()=>{if(epoch!==pluginEpoch||pluginPending)return;button.disabled=true;window.fetch(retry.clone()).then(async response=>{if(!response.ok)return;if(typeof loadProviders==='function')await loadProviders();await window.loadPluginPages?.();await window.loadPlugins?.();}).catch(()=>{});};operationPanel.append(button);}
    mountPluginOperation();
  }
  window.fetch = async function (input, init) {
    const address=new URL(typeof input==='string'?input:input.url,location.href);
    const method=String(init?.method||(typeof input==='object'?input.method:'GET')||'GET').toUpperCase();
    const own=address.origin===location.origin;
    if(own&&method==='POST'&&address.pathname.startsWith('/api/plugins/')&&pluginPending)throw new Error('已有插件操作正在进行，请等待完成');
    const install=own&&method==='POST'&&/^\/api\/plugins\/(add|update|upgrade)$/.test(address.pathname);
    // Preserve an incoming Request's method, headers and body as well as string requests.
    const saved=install?(typeof input==='object'?new Request(input.clone(),init):new Request(address,init)).clone():null;
    const epoch=install?++pluginEpoch:0;
    if(install){pluginPending=true;pluginStatus(epoch,address.pathname.endsWith('/add')?'正在安装插件…':'正在更新插件…','完成后会刷新列表；可继续查看插件说明。');}
    let response;
    try{response=await originalFetch(input,init);}catch(error){
      if(install){pluginPending=false;pluginStatus(epoch,'连接已中断，安装状态待确认','操作可能仍在网关执行。请刷新已安装列表确认结果，再决定是否重试。');}
      throw error;
    }
    if(install){
      pluginPending=false;let result;try{result=await response.clone().json();}catch{pluginStatus(epoch,'响应未完整读取，安装状态待确认','请刷新已安装列表确认结果，再决定是否重试。');return response;}
      if(!response.ok)pluginStatus(epoch,'安装未完成',String(result?.error||result?.message||('HTTP '+response.status)).slice(0,1800),saved);
      else if(!result||!Array.isArray(result.plugins))pluginStatus(epoch,'安装结果格式异常，状态待确认','请刷新已安装列表确认结果。');
      else if(result.error||(result.plugins||[]).some(p=>p.error))pluginStatus(epoch,'安装已结束，请检查加载状态',String(result.error||'列表中有插件未能加载，展开对应插件查看原因。'));
      else pluginStatus(epoch,'安装完成','可在插件列表中继续配置或登录。');
    }
    if (window.magpieAndroidLocal === true && own
        && address.pathname === '/api/backup/export' && response.ok && window.AndroidExport?.backup) {
      response.clone().json().then(result => {
        if (typeof result.path === 'string') window.AndroidExport.backup(result.path);
      }).catch(() => {});
    }
    return response;
  };
  // A user explicitly saves each export through Android's document picker.
  // Preserve blobs until asynchronous file reading has completed.
  const blobs = new Map();
  const create = URL.createObjectURL.bind(URL);
  const revoke = URL.revokeObjectURL.bind(URL);
  URL.createObjectURL = (blob) => { const url = create(blob); blobs.set(url, blob); return url; };
  URL.revokeObjectURL = (url) => { setTimeout(() => { blobs.delete(url); revoke(url); }, 30000); };
  async function exportBlob(blob, name) {
    if (blob.size > 32 * 1024 * 1024) throw new Error('导出文件超过 32 MB，请使用远程浏览器下载');
    const reader = new FileReader();
    reader.onload = () => window.AndroidExport.save(String(reader.result).split(',')[1], blob.type || 'application/octet-stream', name || 'magpie-export.bin');
    reader.onerror = () => alert('读取导出文件失败，请重试');
    reader.readAsDataURL(blob);
  }
  document.addEventListener('click', (event) => {
    const anchor = event.target.closest && event.target.closest('a[download]');
    if (!anchor || !anchor.href.startsWith('blob:') || !window.AndroidExport) return;
    event.preventDefault();
    const blob = blobs.get(anchor.href);
    (blob ? Promise.resolve(blob) : fetch(anchor.href).then(r => r.blob()))
      .then(b => exportBlob(b, anchor.download)).catch(e => alert(e.message));
  }, true);
  window.magpieAndroidDownloadBlob = (url) => {
    const blob = blobs.get(url);
    (blob ? Promise.resolve(blob) : fetch(url).then(r => r.blob()))
      .then(b => exportBlob(b, 'magpie-export.bin')).catch(e => alert(e.message));
  };
  window.magpieAndroidBack = function () {
    const dialogs = Array.from(document.querySelectorAll('dialog[open]'));
    if (dialogs.length) {
      const dialog = dialogs[dialogs.length - 1];
      const cancel = new Event('cancel', {cancelable:true});
      if (dialog.dispatchEvent(cancel)) dialog.close();
      return true;
    }
    const open = Array.from(document.querySelectorAll('dialog[open], .modal:not([hidden]), .pop:not([hidden])'))
      .some(e => e.getBoundingClientRect().height > 0 && getComputedStyle(e).display !== 'none');
    if (!open) return false;
    (document.activeElement || document).dispatchEvent(new KeyboardEvent('keydown', {key:'Escape', code:'Escape', bubbles:true, cancelable:true}));
    return true;
  };
  let leaveVersion=0, leaveDecision=null;
  window.magpieAndroidLeave = async function (request) {
    const version=++leaveVersion;
    let dirty = false;
    if (typeof providerDirty === 'function') dirty = providerDirty();
    if (typeof modalFormDirty === 'function' && document.querySelector('.modal:not([hidden])')) dirty ||= modalFormDirty();
    if (typeof magpie !== 'undefined' && typeof view !== 'undefined') {
      const routing = typeof rtView === 'function' ? rtView(view) : ['routing', 'groups'].includes(view);
      dirty ||= !!magpie.call(routing ? 'routing' : view, 'dirty');
    }
    if (dirty) {
      if (!leaveDecision) {
        leaveDecision=Promise.resolve(typeof confirmDiscard === 'function' ? confirmDiscard() : confirm('尚有未保存的编辑，确定放弃并返回工作台？'));
        leaveDecision.finally(() => { leaveDecision=null; });
      }
      if (!await leaveDecision || version !== leaveVersion) return;
    }
    if (version === leaveVersion) window.AndroidExport?.leave(request);
  };
  window.magpieAndroidNavigation = function () {
    return Array.from(document.querySelectorAll('#nav button[data-view]'))
      .filter(button => !button.hidden && !(typeof featureHides === 'function' && featureHides(button)))
      .map(button => ({id:button.dataset.view, title:button.textContent.trim()}))
      .filter(item => item.id && item.title);
  };
})();
