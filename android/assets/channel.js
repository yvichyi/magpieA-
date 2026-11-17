(function (token) {
  // Android sends this message to the main frame's exact origin. A sandboxed
  // plugin cannot read this closure or forge the per-document handshake.
  function ready(event) {
    if (event.data !== token || event.ports.length !== 1) return;
    window.removeEventListener('message', ready);
    const port = event.ports[0];
    window.AndroidExport = Object.freeze({
      save: (encoded, mime, filename) => port.postMessage(JSON.stringify({action:'save', encoded, mime, filename})),
      backup: path => port.postMessage(JSON.stringify({action:'backup', path})),
      leave: request => port.postMessage(JSON.stringify({action:'leave', request})),
      view: view => port.postMessage(JSON.stringify({action:'view', view}))
    });
  }
  window.addEventListener('message', ready);
})
