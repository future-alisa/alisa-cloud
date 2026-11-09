// Download a pinned public GitHub source archive through the configured proxy.
const https=require('https'),http=require('http'),tls=require('tls'),fs=require('fs'),cp=require('child_process');
const proxy=new URL(process.env.HTTPS_PROXY);const agent=new https.Agent();
agent.createConnection=(options,callback)=>{
 const request=http.request({host:proxy.hostname,port:proxy.port,method:'CONNECT',path:options.host+':'+(options.port||443)});
 request.on('connect',(response,socket)=>{if(response.statusCode!==200){socket.destroy();callback(new Error('Source proxy CONNECT failed'));return;}const secure=tls.connect({socket,servername:options.host});secure.once('secureConnect',()=>callback(null,secure));secure.once('error',callback);});
 request.once('error',callback);request.end();
};
const revision=process.env.SOURCE_REVISION,component=process.env.COMPONENT;
if(!/^[a-f0-9]{40}$/.test(revision)||!['web','api'].includes(component))throw new Error('Invalid source inputs');
const dir='/workspace/source-'+component;fs.rmSync(dir,{recursive:true,force:true});fs.mkdirSync(dir,{recursive:true});
https.get('https://codeload.github.com/future-alisa/alisa-cloud/tar.gz/'+revision,{agent},response=>{
 if(response.statusCode!==200)throw new Error('Source download HTTP '+response.statusCode);
 const archive='/workspace/source-'+component+'.tgz';const file=fs.createWriteStream(archive);response.pipe(file);
 file.on('finish',()=>file.close(()=>{cp.execFileSync('tar',['-xzf',archive,'-C',dir,'--strip-components=1']);console.log('Pinned source ready:',revision,component);}));
}).on('error',error=>{console.error(error.message);process.exitCode=1;});
