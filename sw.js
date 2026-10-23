// KEVANIX Taller — service worker: permite instalar la app y abrirla aunque falle la conexión.
const V = 'kevanix-taller-v1';
const BASE = ['./', 'manifest.webmanifest', 'img/logo.jpg', 'img/emblema-96.png', 'img/icon-192.png', 'img/icon-512.png', 'img/favicon-64.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(V).then(c => c.addAll(BASE))); self.skipWaiting(); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== V).map(k => caches.delete(k)))));
  self.clients.claim();
});
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== location.origin) return; // Firebase y otros: directo a la red
  if (e.request.mode === 'navigate') { // el sistema: siempre la versión nueva; sin conexión, la guardada
    e.respondWith(fetch(e.request).then(r => { const c = r.clone(); caches.open(V).then(x => x.put('./', c)); return r; })
      .catch(() => caches.match('./')));
    return;
  }
  if (u.pathname.includes('/img/') || u.pathname.endsWith('.webmanifest')) {
    e.respondWith(caches.match(e.request).then(r => r || fetch(e.request).then(n => { const c = n.clone(); caches.open(V).then(x => x.put(e.request, c)); return n; })));
  }
});
