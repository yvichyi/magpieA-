// Exercise shipped pages and pluginOptionsField with a deterministic local plugin declaration.
const {chromium}=require('playwright');const fs=require('node:fs');const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',args:['--no-sandbox']});
 try{
  const page=await browser.newPage();
  await page.route('**/__magpie_android/fonts/*',r=>r.fulfill({contentType:'font/ttf',body:fs.readFileSync('android/assets/fonts/'+new URL(r.request().url()).pathname.split('/').pop())}));
  for(const size of [{width:320,height:568},{width:394,height:852},{width:640,height:360},{width:320,height:300}]){
   await page.setViewportSize(size);
   for(const view of ['library','settings','plugins','gateway']){
    await page.goto(process.argv[2]+'/?k=android-test-key-1234567890123456&view='+view);
    await page.waitForFunction(()=>typeof show==='function');
    await page.addStyleTag({path:'android/assets/mobile.css'});await page.addScriptTag({path:'android/assets/mobile.js'});await page.evaluate(()=>document.fonts.ready);
    const host=page.locator('#view-'+view);await host.waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,view+' overflows '+JSON.stringify(size));
    const tools=host.locator(view==='library'?'.lib-headacts button:visible':view==='settings'?'#setTabs button:visible':view==='plugins'?'.pm-head button:visible':'button:visible');
    for(let i=0;i<await tools.count();i++){
     const b=tools.nth(i);await b.scrollIntoViewIfNeeded();const r=await b.boundingBox();assert.ok(r.width>0&&r.x>=-1&&r.x+r.width<=size.width+1,view+' clipped control '+await b.innerText());assert.ok(r.height>=43,view+' undersized control '+await b.innerText());
    }
    if(process.env.MAGPIE_LAYOUT_CAPTURE&&size.width===320&&size.height===568)await page.screenshot({path:process.env.MAGPIE_LAYOUT_CAPTURE+'/'+view+'-320.png'});
   }
   await page.evaluate(()=>show('providers'));await page.evaluate(()=>magpieAndroidProvider('',true));await page.waitForFunction(()=>adding===true);await page.locator('#addSheet button.custom').click();await page.locator('#modal .editor:visible').waitFor();
   await page.evaluate(()=>{
    const declaration={plugin:{name:'Local layout fixture',options:[{key:'available',placement:'top',label:'仅显示可用模型',description:'完整显示插件声明的开关和说明。',on:false},{key:'refresh',placement:'top',label:'自动刷新模型目录',description:'第二个选项用于验证容器按内容撑开。',on:true}]}};
    document.querySelector('#modal .editor .ebody').append(...pluginOptionsField(declaration,true));
   });
   const rows=page.locator('#modal .plugin-options.top .plugin-option');assert.equal(await rows.count(),2);
   for(let i=0;i<2;i++){
    await rows.nth(i).scrollIntoViewIfNeeded();assert.ok(await rows.nth(i).isVisible(),'Top plugin option hidden');
    const buttons=rows.nth(i).locator('button');assert.equal(await buttons.count(),2);for(let j=0;j<2;j++){const b=buttons.nth(j);await b.scrollIntoViewIfNeeded();const r=await b.boundingBox();assert.ok(r.x>=-1&&r.x+r.width<=size.width+1&&r.y>=-1&&r.y+r.height<=size.height+1,'Plugin switch clipped '+JSON.stringify(r));}
   }
   await rows.first().locator('button').last().click();assert.equal(await page.evaluate(()=>draft.pluginOptions.available),true,'Visible option cannot update its real draft');
   const box=await page.locator('.plugin-options.top').boundingBox(),last=await rows.last().boundingBox();assert.ok(box.y+box.height>=last.y+last.height-1,'Plugin options container clips later rows');
   const save=page.locator('#modal .editor .bar button').last();await save.scrollIntoViewIfNeeded();const rect=await save.boundingBox();assert.ok(rect.y>=-1&&rect.y+rect.height<=size.height+1,'Editor Save unreachable');
   if(process.env.MAGPIE_LAYOUT_CAPTURE&&size.width===320&&size.height===568)await page.screenshot({path:process.env.MAGPIE_LAYOUT_CAPTURE+'/plugin-options-320.png'});
  }
  console.log('PASS Actual library/settings/plugins/gateway and editable top plugin switches at 320×568, 394×852, 640×360, 320×300; Save remains reachable');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
