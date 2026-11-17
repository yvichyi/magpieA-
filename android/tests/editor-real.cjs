const {chromium}=require('playwright');
const fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:394,height:852}});
  await page.route('**/__magpie_android/fonts/*',r=>r.fulfill({contentType:'font/ttf',body:fs.readFileSync('android/assets/fonts/'+new URL(r.request().url()).pathname.split('/').pop())}));
  await page.goto(process.argv[2]+'/?k=android-test-key-1234567890123456&view=providers');
  await page.waitForFunction(()=>typeof openProvider==='function');
  await page.addStyleTag({path:'android/assets/mobile.css'});
  await page.addScriptTag({content:fs.readFileSync('android/assets/mobile.js','utf8')});
  if(!await page.evaluate(()=>magpieAndroidProvider('android-qa',false)))throw Error('Editor dispatch refused');
  await page.locator('#providers .row.provider[data-id="android-qa"].selected').waitFor();
  await page.locator('.editor:visible').first().waitFor();
  if(!await page.evaluate(()=>magpieAndroidProvider('',true)))throw Error('Add provider dispatch refused');
  await page.waitForFunction(()=>adding===true);
  await page.locator('#addSheet button.custom').click();
  await page.locator('#modal .editor:visible').waitFor();
  await page.waitForFunction(()=>document.querySelector('#modal .dialog').getAnimations().every(a=>a.playState==='finished'));
  await page.evaluate(()=>document.fonts.ready);
  const layout=await page.locator('#modal .dialog').evaluate(e=>{const b=e.getBoundingClientRect(),bar=e.querySelector('.bar').getBoundingClientRect();return {left:b.left,top:b.top,width:b.width,height:b.height,barBottom:bar.bottom,buttonHeight:e.querySelector('.bar button:last-child').getBoundingClientRect().height};});
  if(Math.abs(layout.left)>1||Math.abs(layout.top)>1||Math.abs(layout.width-394)>1||Math.abs(layout.height-852)>1||layout.barBottom>852||layout.buttonHeight<44)throw Error('Advanced phone editor does not fill the viewport with reachable actions: '+JSON.stringify(layout));
  if(process.env.MAGPIE_EDITOR_CAPTURE)await page.screenshot({path:process.env.MAGPIE_EDITOR_CAPTURE});
  console.log('PASS Real upstream page opens the selected provider editor and add-provider flow through native adapter');
  const groups=await browser.newPage({viewport:{width:320,height:568}});
  await groups.route('**/__magpie_android/fonts/*',r=>r.fulfill({contentType:'font/ttf',body:fs.readFileSync('android/assets/fonts/'+new URL(r.request().url()).pathname.split('/').pop())}));
  async function groupPage(){
   await groups.goto(process.argv[2]+'/?k=android-test-key-1234567890123456&view=groups');
   await groups.addStyleTag({path:'android/assets/mobile.css'});await groups.addScriptTag({path:'android/assets/mobile.js'});
   await groups.locator('#view-groups .rt-gnew').waitFor();await groups.evaluate(()=>document.fonts.ready);
  }
  await groupPage();await groups.locator('#view-groups .rt-group[data-id="android-qa-fallback"]').waitFor();
  if(!await groups.evaluate(()=>magpieAndroidGroup('android-qa-fallback',false)))throw Error('Exact group edit dispatch refused');
  await groups.locator('#view-groups .rt-gedit').waitFor();
  if(!await groups.locator('#view-groups .rt-gedit input').first().inputValue())throw Error('Existing group editor lost its name');
  await groupPage();if(!await groups.evaluate(()=>magpieAndroidGroup('',true)))throw Error('New group dispatch refused');await groups.locator('#view-groups .rt-gedit').waitFor();
  if(await groups.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Phone group editor overflows viewport');
  if(process.env.MAGPIE_GROUP_CAPTURE)await groups.screenshot({path:process.env.MAGPIE_GROUP_CAPTURE});
  console.log('PASS Actual group page opens exact existing group and new group editors directly at320px');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
