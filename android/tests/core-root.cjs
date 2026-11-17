const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../..');
module.exports=process.env.CORE_SOURCE_DIR||(fs.existsSync(path.join(root,'magpie-core/main.go'))?path.join(root,'magpie-core'):root);
