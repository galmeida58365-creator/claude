// Roda dentro da página do vídeo, aberta no WebView do app (como num navegador).
// Devolve um texto JSON com os endereços do vídeo, do melhor para o pior,
// ou null enquanto a página ainda não carregou o vídeo.
//
// Fonte principal: os dados que a própria página traz embutidos
// (__UNIVERSAL_DATA_FOR_REHYDRATION__). Reserva: o <video> que está tocando.

function dadosDaPagina(doc) {
  var el = doc.getElementById('__UNIVERSAL_DATA_FOR_REHYDRATION__');
  if (el) {
    try {
      var escopo = JSON.parse(el.textContent).__DEFAULT_SCOPE__ || {};
      var det = escopo['webapp.video-detail'];
      if (det) {
        return { status: det.statusCode || 0, item: det.itemInfo && det.itemInfo.itemStruct };
      }
    } catch (e) { /* página ainda montando */ }
  }
  var sigi = doc.getElementById('SIGI_STATE') || doc.getElementById('sigi-persisted-data');
  if (sigi) {
    try {
      var modulo = JSON.parse(sigi.textContent).ItemModule || {};
      var chave = Object.keys(modulo)[0];
      if (chave) return { status: 0, item: modulo[chave] };
    } catch (e) { /* idem */ }
  }
  return null;
}

// "v0f025gc0000..._h264_1080p_1534422" -> {codec: "h264", res: 1080, br: 1534422}
function lerChave(chave) {
  var m = /v[^_]+_([^_]+)_(\d+)p_(\d+)/.exec(chave || '');
  if (!m) return {};
  var res = parseInt(m[2], 10);
  return { codec: m[1], res: res === 540 ? 576 : res, br: parseInt(m[3], 10) };
}

function ehHttp(u) {
  return typeof u === 'string' && /^https?:\/\//.test(u) && !/^https?:\/\/www\.tiktok\.com\//.test(u);
}

// Lista de endereços sem marca d'água, do melhor para o pior:
// maior resolução primeiro; na mesma resolução, H.264 (toca em qualquer
// celular) antes do H.265; depois, maior taxa de bits. O bytevc2 não toca
// em lugar nenhum e fica de fora; a versão com marca d'água só entra no fim.
function escolherVideos(item) {
  var video = (item && item.video) || {};
  var opcoes = [];
  (video.bitrateInfo || []).forEach(function (b) {
    var addr = b.PlayAddr || {};
    var info = lerChave(addr.UrlKey);
    var codec = (info.codec || b.CodecType || '').toLowerCase();
    if (codec.indexOf('bytevc2') >= 0) return;
    var res = info.res || Math.min(addr.Width || 0, addr.Height || 0) || 0;
    (addr.UrlList || []).forEach(function (u) {
      if (!ehHttp(u) || /\/media-video-hvc1\//.test(u)) return; // hvc1: às vezes vem sem som
      opcoes.push({
        url: u, res: res,
        h264: codec.indexOf('h264') >= 0 ? 1 : 0,
        br: info.br || b.Bitrate || 0,
      });
    });
  });
  opcoes.sort(function (a, b) { return (b.res - a.res) || (b.h264 - a.h264) || (b.br - a.br); });

  var urls = opcoes.map(function (o) { return o.url; });
  var play = video.playAddr;
  [].concat(play || []).forEach(function (p) {
    var u = typeof p === 'string' ? p : p && p.src;
    if (ehHttp(u)) urls.push(u);
  });
  var marca = [];
  [video.downloadAddr, video.download && video.download.url].forEach(function (u) {
    if (ehHttp(u)) marca.push(u);
  });
  var vistos = {};
  var limpar = function (lista) {
    return lista.filter(function (u) { return vistos[u] ? false : (vistos[u] = true); });
  };
  return { urls: limpar(urls), marca: limpar(marca) };
}

function extrair(doc) {
  var dados = dadosDaPagina(doc);
  if (dados) {
    if (dados.status && dados.status !== 0) return { erro: 'status', status: dados.status };
    var item = dados.item;
    if (item) {
      if (item.imagePost) return { erro: 'fotos' };
      var escolha = escolherVideos(item);
      var autor = item.author;
      var v = item.video || {};
      if (escolha.urls.length || escolha.marca.length) {
        return {
          id: String(item.id || ''),
          titulo: item.desc || '',
          autor: (autor && (autor.uniqueId || autor.nickname)) || (typeof autor === 'string' ? autor : ''),
          largura: v.width || 0,
          altura: v.height || 0,
          urls: escolha.urls,
          marca: escolha.marca,
        };
      }
    }
  }
  // Reserva: o vídeo que a página está tocando.
  var tag = doc.querySelector('video');
  var src = tag && (tag.currentSrc || tag.src || (tag.querySelector('source') || {}).src);
  if (ehHttp(src)) {
    return { id: '', titulo: doc.title || '', autor: '', largura: tag.videoWidth || 0,
             altura: tag.videoHeight || 0, urls: [src], marca: [] };
  }
  return null;
}

// No app, o Java põe este arquivo dentro de uma função e chama
// JSON.stringify(extrair(document)). Nos testes (Node), exporta as funções.
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { extrair: extrair, escolherVideos: escolherVideos, lerChave: lerChave };
}
