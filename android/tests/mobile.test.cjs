const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const coreRoot=require('./core-root.cjs');
const {chromium}=require('playwright');
const adapter=fs.readFileSync(process.env.MOBILE_ADAPTER_PATH||path.join(__dirname,'../assets/mobile.js'),'utf8');
const mobileCss=fs.readFileSync(path.join(__dirname,'../assets/mobile.css'),'utf8');
const options={headless:true,executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',args:['--no-sandbox']};

test('Native group dispatch targets the separate group editor and waits for its page',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();
 await p.setContent('<section id="view-groups"><button class="rt-gnew">New group</button><article class="rt-group"></article></section>');
 await p.evaluate(()=>{window.edited=[];window.added=0;const row=document.querySelector('.rt-group');row.dataset.id='group/"精确标识"';row.onclick=()=>edited.push(row.dataset.id);document.querySelector('.rt-gnew').onclick=()=>added++});
 await p.addScriptTag({content:adapter});assert.equal(await p.evaluate(()=>typeof magpieAndroidGroup),'function','Native groups have no direct editor dispatch');
 assert.equal(await p.evaluate(()=>magpieAndroidGroup('group/"精确标识"',false)),true);assert.deepEqual(await p.evaluate(()=>edited),['group/"精确标识"']);
 assert.equal(await p.evaluate(()=>magpieAndroidGroup('',true)),true);assert.equal(await p.evaluate(()=>added),1);
 assert.equal(await p.evaluate(()=>magpieAndroidGroup('missing',false)),false);
 await p.locator('#view-groups').evaluate(e=>e.hidden=true);assert.equal(await p.evaluate(()=>magpieAndroidGroup('',true)),false,'A wrong/hidden page must not consume the editor intent');
});

test('Native model editing delegates the exact identifier to the upstream editor',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();await p.setContent('<main></main>');
 await p.evaluate(()=>{window.edited=[];window.added=0;window.openProvider=id=>edited.push(id);window.addProviderFromAgents=()=>added++});
 await p.addScriptTag({content:adapter});
 const id='供应商/"quoted"';assert.equal(await p.evaluate(id=>magpieAndroidProvider(id,false),id),true);
 assert.deepEqual(await p.evaluate(()=>edited),[id]);assert.equal(await p.evaluate(()=>magpieAndroidProvider('',true)),true);assert.equal(await p.evaluate(()=>added),1);
});

test('Native Android chrome replaces upstream chrome while preserving navigation DOM',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();
 await p.setContent('<html data-theme="light"><body class="window web side-nav" style="padding-left:212px"><header class="top"><nav id="nav"><button data-view="providers">Providers</button></nav></header><aside class="sidebar">Sidebar</aside><main class="view">Providers</main></body></html>');
 await p.addStyleTag({content:mobileCss});await p.addScriptTag({content:adapter});
 assert.equal(await p.locator('.top').evaluate(e=>getComputedStyle(e).display),'none');
 assert.equal(await p.locator('.sidebar').evaluate(e=>getComputedStyle(e).display),'none');
 assert.equal(await p.locator('body').evaluate(e=>getComputedStyle(e).paddingLeft),'0px');
 assert.equal(await p.locator('#nav [data-view="providers"]').count(),1);
 assert.equal(await p.locator('html').evaluate(e=>getComputedStyle(e).getPropertyValue('--bg').trim()),'#ffffff');
});

test('Native navigation keeps installed plugin pages reachable',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();
 await p.setContent('<nav id="nav"><button data-view="providers"><span>供应商</span></button><button data-view="plugin-review-board"><span>Review board</span></button><button data-view="unavailable" hidden>Hidden</button></nav>');
 await p.addScriptTag({content:adapter});
 assert.equal(await p.evaluate(()=>typeof magpieAndroidNavigation),'function');
 const items=await p.evaluate(()=>magpieAndroidNavigation());
 assert.deepEqual(items,[{id:'providers',title:'供应商'},{id:'plugin-review-board',title:'Review board'}]);
});

test('Leaving Routing groups confirms the dirty state owned by the routing module',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();await p.setContent('<main></main>');
 await p.evaluate(()=>{window.view='groups';window.magpie={call:(id,hook)=>id==='routing'&&hook==='dirty'};window.confirmations=0;window.left=0;window.confirmDiscard=async()=>{confirmations++;return false};window.AndroidExport={leave:()=>left++}});
 await p.addScriptTag({content:adapter});await p.evaluate(()=>magpieAndroidLeave());
 assert.equal(await p.evaluate(()=>confirmations),1);assert.equal(await p.evaluate(()=>left),0);
 await p.evaluate(()=>{window.confirmDiscard=async()=>true;return magpieAndroidLeave()});assert.equal(await p.evaluate(()=>left),1);
});

test('Android export preserves exact Unicode bytes and a filename',async t=>{
  const b=await chromium.launch(options);t.after(()=>b.close());
  const p=await b.newPage();await p.setContent('<html><body></body></html>');
  await p.evaluate(()=>{window.exports=[];window.AndroidExport={save:(...a)=>exports.push(a)}});
  await p.addScriptTag({content:adapter});
  await p.evaluate(()=>{const a=document.createElement('a');a.download='用量.csv';a.href=URL.createObjectURL(new Blob(['模型,次数\n测试,3\n'],{type:'text/csv'}));document.body.append(a);a.click();URL.revokeObjectURL(a.href)});
  await p.waitForFunction(()=>window.exports.length===1);
  const [encoded,mime,name]=await p.evaluate(()=>exports[0]);
  assert.equal(Buffer.from(encoded,'base64').toString(),'模型,次数\n测试,3\n');assert.equal(mime,'text/csv');assert.equal(name,'用量.csv');
});

test('Local backup response opens Android export for the newly sealed backup',async t=>{
  const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();
  await p.route('http://127.0.0.1:3430/**',route=>route.fulfill({contentType:route.request().url().endsWith('/export')?'application/json':'text/html',body:route.request().url().endsWith('/export')?'{"path":"~/Downloads/magpie-2026-11-09.magpie-backup"}':'<html><body></body></html>'}));
  await p.goto('http://127.0.0.1:3430/');
  await p.evaluate(()=>{window.magpieAndroidLocal=true;window.backups=[];window.AndroidExport={backup:p=>backups.push(p)}});
  await p.addScriptTag({content:adapter});
  const result=await p.evaluate(async()=>{const r=await fetch('/api/backup/export',{method:'POST',body:'{}'});return r.json()});
  assert.equal(result.path,'~/Downloads/magpie-2026-11-09.magpie-backup');
  await p.waitForTimeout(100);
  assert.deepEqual(await p.evaluate(()=>window.backups),['~/Downloads/magpie-2026-11-09.magpie-backup']);
});

test('Android Back dispatches Escape only while a dialog is visible',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();await p.setContent('<div class="modal" hidden>dialog</div>');await p.addScriptTag({content:adapter});
 assert.equal(await p.evaluate(()=>magpieAndroidBack()),false);
 await p.evaluate(()=>{document.querySelector('.modal').hidden=false;window.escapes=0;document.addEventListener('keydown',e=>{if(e.key==='Escape')escapes++})});
 assert.equal(await p.evaluate(()=>magpieAndroidBack()),true);assert.equal(await p.evaluate(()=>escapes),1);
});

test('Android Back invokes actual dialog cancellation and focused picker handlers',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();
 await p.setContent('<dialog><button>Cancel</button></dialog><div class="pop" hidden><input id="q"></div>');await p.addScriptTag({content:adapter});
 await p.evaluate(()=>{const d=document.querySelector('dialog');d.addEventListener('cancel',e=>{e.preventDefault();d.close()});d.showModal()});
 assert.equal(await p.evaluate(()=>magpieAndroidBack()),true);
 assert.equal(await p.locator('dialog').evaluate(d=>d.open),false);
 await p.evaluate(()=>{document.querySelector('.pop').hidden=false;const q=document.querySelector('#q');q.focus();q.addEventListener('keydown',e=>{if(e.key==='Escape')document.querySelector('.pop').hidden=true})});
 assert.equal(await p.evaluate(()=>magpieAndroidBack()),true);assert.equal(await p.locator('.pop').evaluate(e=>e.hidden),true);
});

test('Export channel refuses forged frame handshakes and is absent in sandboxed plugins',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();
 await p.setContent('<iframe sandbox="allow-scripts" srcdoc="<script>parent.postMessage(typeof window.AndroidExport,\'*\')</script>"></iframe>');
 const channel=fs.readFileSync(path.join(__dirname,'../assets/channel.js'),'utf8');
 await p.addScriptTag({content:channel+'("private-per-document-token");'});
 await p.evaluate(()=>{const c=new MessageChannel();window.postMessage('forged-token','*',[c.port2]);});
 await p.waitForTimeout(50);assert.equal(await p.evaluate(()=>typeof AndroidExport),'undefined');
 await p.evaluate(()=>{window.received=[];const c=new MessageChannel();c.port1.onmessage=e=>received.push(JSON.parse(e.data));window.postMessage('private-per-document-token','*',[c.port2]);});
 await p.waitForFunction(()=>typeof AndroidExport==='object');
 await p.evaluate(()=>AndroidExport.backup('~/Downloads/magpie-2026-11-09.magpie-backup'));
 await p.waitForFunction(()=>received.length===1);
 assert.deepEqual(await p.evaluate(()=>received[0]),{action:'backup',path:'~/Downloads/magpie-2026-11-09.magpie-backup'});
 const result=await p.frames()[1].evaluate(()=>typeof window.AndroidExport);assert.equal(result,'undefined');
});


test('Rapid native exits share one dirty confirmation and only authorize the latest destination',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();await p.setContent('<main></main>');
 await p.evaluate(()=>{window.requests=[];window.confirmations=0;window.providerDirty=()=>true;window.confirmDiscard=()=>{confirmations++;return new Promise(resolve=>window.discard=resolve)};window.AndroidExport={leave:r=>requests.push(r)}});
 await p.addScriptTag({content:adapter});
 await p.evaluate(()=>{window.first=magpieAndroidLeave('models');window.second=magpieAndroidLeave('usage')});
 assert.equal(await p.evaluate(()=>confirmations),1);
 await p.evaluate(()=>discard(true));await p.evaluate(()=>Promise.all([first,second]));
 assert.deepEqual(await p.evaluate(()=>requests),['usage']);
});

test('Android plugin market uses readable full-width rows and reachable install controls',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage({viewport:{width:394,height:852}});
 await p.setContent('<body class="web magpie-android"><main id="view-plugins"><div class="pm-grid"><article class="pm-card"><div class="pm-name"><b>Long plugin name for an Android workspace</b></div><p class="pm-sum">Plugin description</p><div class="pm-foot"><button>安装</button></div></article></div></main></body>');
 await p.addStyleTag({path:path.join(coreRoot,'internal/gui/assets/plugins.css')});await p.addStyleTag({content:mobileCss});
 const sizes=await p.evaluate(()=>{const c=document.querySelector('.pm-card'),b=c.querySelector('button');return {font:parseFloat(getComputedStyle(c.querySelector('b')).fontSize),height:b.getBoundingClientRect().height,border:getComputedStyle(c).borderRadius,columns:getComputedStyle(document.querySelector('.pm-grid')).gridTemplateColumns}});
 assert.ok(sizes.font>=16,'Plugin names remain desktop-sized');assert.ok(sizes.height>=44,'Install control is too small');assert.equal(sizes.border,'0px');
});

test('Plugin installation reports real failure and retries only after explicit user action',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();let attempts=0;
 await p.route('http://magpie.test/**',async route=>{if(route.request().method()==='POST'){attempts++;await route.fulfill({status:attempts===1?500:200,contentType:'application/json',body:JSON.stringify(attempts===1?{error:'permission denied <b>runtime</b>'}:{plugins:[]})});}else await route.fulfill({contentType:'text/html',body:'<body><main id="view-plugins"></main></body>'});});
 await p.goto('http://magpie.test/');await p.addScriptTag({content:adapter});
 await p.evaluate(()=>fetch('/api/plugins/add',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({spec:'test-plugin'})}));
 await p.waitForSelector('#android-plugin-operation');assert.equal(attempts,1);
 assert.match(await p.locator('#android-plugin-operation').innerText(),/permission denied/);assert.equal(await p.locator('#android-plugin-operation b').count(),0);
 await p.getByRole('button',{name:'重试安装'}).click();await p.waitForFunction(()=>document.querySelector('#android-plugin-operation')?.textContent.includes('安装完成'));
 assert.equal(attempts,2);
});

test('A slow plugin install never sends an overlapping mutation',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();let attempts=0,release;
 await p.route('http://magpie.test/**',async r=>{if(r.request().method()==='POST'){attempts++;await new Promise(resolve=>release=resolve);await r.fulfill({contentType:'application/json',body:'{"plugins":[]}'});}else await r.fulfill({contentType:'text/html',body:'<main id="view-plugins"></main>'});});
 await p.goto('http://magpie.test/');await p.addScriptTag({content:adapter});
 await p.evaluate(()=>{window.installing=fetch('/api/plugins/add',{method:'POST',body:'{"spec":"one"}'})});await p.waitForSelector('#android-plugin-operation.pending');
 const error=await p.evaluate(()=>fetch('/api/plugins/add',{method:'POST',body:'{"spec":"two"}'}).then(()=>'',e=>e.message));assert.match(error,/正在进行/);assert.equal(attempts,1);
 release();await p.evaluate(()=>installing);assert.match(await p.locator('#android-plugin-operation').innerText(),/安装完成/);
});

test('A disconnected install remains uncertain without an automatic retry',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();let attempts=0;
 await p.route('http://magpie.test/**',async r=>{if(r.request().method()==='POST'){attempts++;await r.abort('failed');}else await r.fulfill({contentType:'text/html',body:'<main id="view-plugins"></main>'});});
 await p.goto('http://magpie.test/');await p.addScriptTag({content:adapter});await p.evaluate(()=>fetch('/api/plugins/add',{method:'POST',body:'{"spec":"one"}'}).catch(()=>{}));
 assert.match(await p.locator('#android-plugin-operation').innerText(),/状态待确认/);assert.equal(await p.getByRole('button',{name:'重试安装'}).count(),0);assert.equal(attempts,1);
});

test('An HTTP200 install with a truncated body never claims success',async t=>{
 const http=require('http');const server=http.createServer((req,res)=>{if(req.method==='POST'){res.writeHead(200,{'Content-Type':'application/json','Content-Length':'1000'});res.flushHeaders();res.write('{"plugins":');setTimeout(()=>res.destroy(),60);}else {res.writeHead(200,{'Content-Type':'text/html'});res.end('<main id="view-plugins"></main>');}});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();await p.goto('http://127.0.0.1:'+server.address().port);await p.addScriptTag({content:adapter});
 await p.evaluate(()=>fetch('/api/plugins/add',{method:'POST',body:'{"spec":"one"}'}).then(r=>r.text()).catch(()=>{}));
 const message=await p.locator('#android-plugin-operation').innerText();assert.doesNotMatch(message,/安装完成/);assert.match(message,/待确认|状态未知/);
});

test('A successful retry refreshes providers so the newly installed plugin can sign in',async t=>{
 const vm=require('node:vm'),{createRequire}=require('node:module');const file=path.resolve(coreRoot,'internal/gui/tests/plugin-market.test.cjs');
 const prefix=fs.readFileSync(file,'utf8').split('for (const engine of ')[0];const fixture={module:{exports:{}},require:createRequire(file),__dirname:path.dirname(file),process,console,setTimeout,URL,Buffer};vm.runInNewContext(prefix+'\nmodule.exports=server;',fixture);
 const base=fixture.module.exports('en',[]);let attempts=0;const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage();p.setDefaultTimeout(7000);
 await p.route('**/*',async route=>{const u=new URL(route.request().url());if(u.pathname==='/api/plugins/add'&&++attempts===1)return route.fulfill({status:500,json:{error:'intentional first install failure'}});await base({request:()=>route.request(),fulfill:args=>route.fulfill(u.pathname==='/api/plugins/add'&&args.json?{...args,json:{plugins:[]}}:args)});});
 await p.goto('http://magpie.test/?view=plugins');await p.addScriptTag({content:adapter});await p.addStyleTag({content:mobileCss});assert.ok(await p.locator('#view-plugins .lib-tabs').isVisible(),'Native chrome hides plugin sub-tabs together with the upstream sidebar');await p.evaluate(()=>window.pluginQuery('Copilot'));
 const plugin=p.locator('.pm-card[data-pkg="opencode-copilot-auth"]');await plugin.getByRole('button',{name:'Install',exact:true}).click();await p.getByRole('button',{name:'重试安装'}).waitFor();
 const refreshed=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/providers');await p.getByRole('button',{name:'重试安装'}).click();await refreshed;
 await plugin.getByRole('button',{name:'Sign in',exact:true}).waitFor();assert.equal(attempts,2);
});

test('Installed plugins show a flat readable list with actions below the name',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage({viewport:{width:394,height:780}});
 await p.setContent('<body class="web magpie-android side-nav"><main id="view-plugins"><div class="list pm-list"><div class="row pm-row"><div class="pm-logo"></div><div class="who"><div class="name"><span>long-plugin-package-with-a-readable-name</span><span class="pm-ver">v1.0.0</span></div><div class="sub">插件说明与接口信息</div></div><div class="val"><button>选项</button><button>停用</button><button>移除</button></div></div></div></main></body>');
 await p.addStyleTag({path:path.join(coreRoot,'internal/gui/assets/app.css')});await p.addStyleTag({path:path.join(coreRoot,'internal/gui/assets/plugins.css')});await p.addStyleTag({content:mobileCss});
 const info=await p.evaluate(()=>{const list=document.querySelector('.pm-list'),who=list.querySelector('.who'),val=list.querySelector('.val');return {radius:getComputedStyle(list).borderRadius,width:who.getBoundingClientRect().width,after:val.getBoundingClientRect().top>=who.getBoundingClientRect().bottom,wrap:getComputedStyle(who.querySelector('.name>span')).whiteSpace};});
 assert.equal(info.radius,'0px','Installed plugins remain inside a rounded content card');assert.ok(info.width>230,'Actions squeeze the plugin name');assert.ok(info.after,'Actions must not consume the name column');assert.equal(info.wrap,'normal');
});

test('Plugin toolbar stays aligned in gateway mode as well as sidebar mode',async t=>{
 const b=await chromium.launch(options);t.after(()=>b.close());const p=await b.newPage({viewport:{width:394,height:780}});
 await p.setContent('<body class="window web magpie-android gateway-mode"><main id="view-plugins"><div class="lib-head pm-head"><div class="segs lib-tabs"><span class="thumb"></span><button class="opt">内置</button><button class="opt">发现</button><button class="opt on">已安装</button></div><span class="grow"></span><button class="pm-mirror">国内镜像</button><label class="pm-find"><input placeholder="搜索插件和 npm…"></label></div></main></body>');
 await p.addStyleTag({path:path.join(coreRoot,'internal/gui/assets/app.css')});await p.addStyleTag({path:path.join(coreRoot,'internal/gui/assets/plugins.css')});await p.addStyleTag({content:mobileCss});
 for(const sidebar of [false,true]){
  await p.evaluate(on=>document.body.classList.toggle('side-nav',on),sidebar);
  const info=await p.evaluate(()=>{const h=document.querySelector('.pm-head'),q=h.querySelector('.pm-find'),m=h.querySelector('.pm-mirror');return {display:getComputedStyle(h).display,search:q.getBoundingClientRect().top,mirror:m.getBoundingClientRect().top,thumb:getComputedStyle(h.querySelector('.thumb')).display};});
  assert.equal(info.display,'grid','Gateway mode must receive the same mobile toolbar layout');assert.equal(info.search,info.mirror,'Mirror and search must share one row');assert.equal(info.thumb,'none','Tabs retain a rounded desktop selection card');
 }
});
