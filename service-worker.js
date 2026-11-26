/* PESS V35 service worker — app shell + runtime asset cache */
const CACHE_PREFIX='pess-';
const SHELL_CACHE='pess-v35-shell';
const RUNTIME_CACHE='pess-v35-runtime';
const SHELL=['./','./index.html','./manifest.webmanifest','./pess-icon.svg','./pess-icon-192.png','./pess-icon-512.png'];
const CDN_HOSTS=new Set(['cdnjs.cloudflare.com','cdn.jsdelivr.net','unpkg.com','fonts.googleapis.com','fonts.gstatic.com','cdn.tailwindcss.com']);
self.addEventListener('install',event=>event.waitUntil((async()=>{const c=await caches.open(SHELL_CACHE);await c.addAll(SHELL);await self.skipWaiting();})()));
self.addEventListener('activate',event=>event.waitUntil((async()=>{const keys=await caches.keys();await Promise.all(keys.filter(k=>k.startsWith(CACHE_PREFIX)&&k!==SHELL_CACHE&&k!==RUNTIME_CACHE).map(k=>caches.delete(k)));await self.clients.claim();})()));
async function save(cacheName,request,response){if(response&&(response.ok||response.type==='opaque')){const c=await caches.open(cacheName);await c.put(request,response.clone());}return response;}
self.addEventListener('fetch',event=>{const req=event.request;if(req.method!=='GET')return;const url=new URL(req.url);
 if(req.mode==='navigate'&&url.origin===self.location.origin){event.respondWith((async()=>{try{const r=await fetch(req);if(r&&r.ok){const c=await caches.open(SHELL_CACHE);await c.put('./index.html',r.clone());}return r;}catch(_){return (await caches.match('./index.html'))||new Response('التطبيق غير متاح حالياً. افتح PESS مرة واحدة مع اتصال الإنترنت.',{status:503,headers:{'Content-Type':'text/plain; charset=utf-8'}});}})());return;}
 if(url.origin===self.location.origin){event.respondWith((async()=>{const hit=await caches.match(req);if(hit)return hit;try{return await save(RUNTIME_CACHE,req,await fetch(req));}catch(_){return new Response('',{status:503,statusText:'Offline'});}})());return;}
 if(CDN_HOSTS.has(url.hostname)){event.respondWith((async()=>{const c=await caches.open(RUNTIME_CACHE);const hit=await c.match(req);if(hit){event.waitUntil(fetch(req).then(r=>save(RUNTIME_CACHE,req,r)).catch(()=>{}));return hit;}try{return await save(RUNTIME_CACHE,req,await fetch(req));}catch(_){return new Response('',{status:503,statusText:'Offline asset not cached'});}})());}
});
self.addEventListener('message',event=>{if(event.data==='PESS_SKIP_WAITING')self.skipWaiting();});
