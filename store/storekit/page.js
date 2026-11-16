/* N7 Glyph store kit: builds ONE store picture inside #n7k-pic from window.N7_PAGE, with the design-system components.
   render.py writes the page and headless Edge captures it. The page answers through #n7k-status (green = finished,
   red = failed) and through one console line "N7STORE:<base64 json>" with warnings, the error text and layout metrics. */
(function () {
  'use strict';

  var page = window.N7_PAGE || {};
  var report = { kind: page.kind, warnings: [], metrics: {} };

  function send(error) {
    if (error) report.error = String((error && error.message) || error);
    report.ok = !error;
    document.getElementById('n7k-status').style.background = error ? '#ff0000' : '#00ff00';
    var bytes = new TextEncoder().encode(JSON.stringify(report)), bin = '';
    for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    console.log('N7STORE:' + btoa(bin));
  }

  if (!window.React || !window.ReactDOM || !window.N7Glyph) {
    send('React or the design-system bundle did not load (store/vendor, store/ds/components/bundle.js)');
    return;
  }

  var React = window.React, ReactDOM = window.ReactDOM, N7 = window.N7Glyph;
  var h = React.createElement;
  var pic = document.getElementById('n7k-pic');
  var root = ReactDOM.createRoot(pic);
  function draw(element) { ReactDOM.flushSync(function () { root.render(element); }); }

  // ---------- canvas facts (StoreShot is 1080x1920 with an 80px side padding, see bundle.css) ----------
  var W = 1080, H = 1920;
  var SIDE = 80;
  var HEAD_RIGHT = 1040;   // a headline line, red stop included, ends here at the latest ("FEELINGS." fits at 190px)
  var MIN_GAP = 96;        // the picture never starts closer than this under the text block
  // Where the text block ends (bottom of the sub line box) in the two reference cases, Glyph Pet's shots:
  // TB2 = two headline lines + two sub lines, TB3 = three headline lines + two sub lines.
  var TB2 = 701, TB3 = 881.5;

  // ---------- helpers ----------
  function at(left, top, child, key, className) {
    return h('div', { key: key, className: className || 'n7k-abs', style: { left: left, top: top } }, child);
  }

  function loadFonts() {
    var specs = ['900 190px "Doto"', '400 44px "Geist"', '700 120px "Geist"', '500 28px "Geist Mono"'];
    return Promise.all(specs.map(function (s) { return document.fonts.load(s); }))
      .then(function () { return document.fonts.ready; })
      .then(function () {
        var loaded = {};
        document.fonts.forEach(function (f) { if (f.status === 'loaded') loaded[f.family.replace(/["']/g, '')] = true; });
        ['Doto', 'Geist', 'Geist Mono'].forEach(function (family) {
          if (!loaded[family]) throw new Error('the font "' + family + '" did not load (tokens.css takes it from store/ds/fonts)');
        });
      });
  }

  function loadImage(src) {
    return new Promise(function (resolve, reject) {
      var img = new Image();
      img.onload = function () { resolve(img); };
      img.onerror = function () { reject(new Error('the image did not load: ' + src)); };
      img.src = src;
    });
  }

  /** Shrinks a dot headline when a line is wider than maxWidth or the caller's `tooTall` says so. The house size is the start. */
  function fitHeadline(selector, maxWidth, tooTall) {
    var el = pic.querySelector(selector);
    var base = parseFloat(getComputedStyle(el).fontSize), size = base, rule = null;
    function widest() {
      var w = 0;
      Array.prototype.forEach.call(el.querySelectorAll('.n7-hl-line'), function (line) {
        var range = document.createRange();
        range.selectNodeContents(line);
        w = Math.max(w, range.getBoundingClientRect().width);
      });
      return w;
    }
    function set(px) {
      if (!rule) { rule = document.createElement('style'); document.head.appendChild(rule); }
      rule.textContent = '#n7k-pic ' + selector + ' { font-size: ' + px + 'px; }';
      size = px;
    }
    var w = widest();
    if (w > maxWidth + 0.5) set(Math.floor(base * maxWidth / w));
    while (tooTall && tooTall() && size > base * 0.4) set(size - 2);
    if (size !== base) report.warnings.push('the headline is set at ' + size + 'px instead of the house ' + base + 'px: it did not fit');
    report.metrics.headlinePx = size;
  }

  /** A GlyphMatrix of the app's (white-only) cells. The glow is on unless `glow` is false (grids are drawn crisp, like Glyph Pet's). */
  function matrix(size, cells, glow) { return h(N7.GlyphMatrix, { size: size, cells: cells, glow: glow !== false }); }

  // ---------- screenshot layouts: each gets the bottom of the text block and draws the stage under it ----------
  var LAYOUTS = {

    /* The phone photo with the frame on its lens, and the same matrix zoomed, overlapping to the right.
       Numbers are Glyph Pet's (fitted to within 5 levels a pixel): phone 600 wide from y 960 and a 540 matrix under a
       two-line headline, 560 / 1020 / 520 under a three-line one. In between and beyond, the same slope. */
    phone: function (tb, stage) {
      var k = (tb - TB2) / (TB3 - TB2);
      var width = Math.round(600 - 40 * k);
      var top = Math.round(Math.max(960 + 60 * k, tb + MIN_GAP));
      var zoom = Math.min(Math.round(540 - 20 * k), Math.floor(1700 - 10 - tb - MIN_GAP / 2));
      report.metrics.phone = { left: 70, top: top, width: width, zoom: zoom };
      stage([
        at(70, top, h(N7.PhoneBack, { width: width, cells: page.cells, src: page.phone }), 'phone'),
        at(1030 - zoom - 10, 1700 - zoom - 10, matrix(zoom, page.cells), 'zoom', 'n7k-zoom')
      ]);
    },

    /* One huge matrix in the middle of the stage. */
    zoom: function (tb, stage) {
      var size = Math.min(800, Math.floor(H - 60 - tb - MIN_GAP));
      var top = Math.round(Math.max(tb + MIN_GAP, tb + (H - tb - size) / 2));
      report.metrics.zoom = { top: top, size: size };
      stage(at((W - size) / 2, top, matrix(size, page.cells), 'zoom'));
    },

    /* 2 to 9 matrices with a mono caps label under each. Three in a row (two for 2 and 4 pictures), a short last row is centred.
       Sizes, gaps and tops are Glyph Pet's shots 5 (one row), 4 (two rows) and 2 (three rows). */
    grid: function (tb, stage) {
      var items = page.items, n = items.length;
      var cols = n === 2 || n === 4 ? 2 : 3, rows = Math.ceil(n / cols);
      var labelled = items.some(function (it) { return it.label; });
      var size = cols === 3 ? [300, 280, 270][rows - 1] : [440, 380][rows - 1];
      var gap = cols === 3 ? 30 : 40;
      var between = labelled ? (rows === 3 ? 84 : 104) : 40;   // from the bottom of a matrix to the top of the next row
      var tail = labelled ? 47 : 0;                            // the last row's label: 25px under the matrix, caps 22px tall
      var bottom = H - 15;
      function block() { return rows * size + (rows - 1) * between + tail; }
      var top = Math.max(tb + 179, tb + (H - tb - block()) / 2 + [13, 5, 0][rows - 1]);
      if (top + block() > bottom) top = bottom - block();
      if (top < tb + MIN_GAP) {
        top = tb + MIN_GAP;
        size = Math.floor((bottom - top - (rows - 1) * between - tail) / rows);
        report.warnings.push('the grid matrices are shrunk to ' + size + 'px to fit under the text');
      }
      top = Math.round(top);
      report.metrics.grid = { cols: cols, rows: rows, size: size, top: top };
      var kids = [];
      items.forEach(function (it, i) {
        var row = Math.floor(i / cols), inRow = Math.min(cols, n - row * cols);
        var left = (W - (inRow * size + (inRow - 1) * gap)) / 2 + (i - row * cols) * (size + gap);
        var y = top + row * (size + between);
        kids.push(at(left, y, matrix(size, it.cells, false), 'm' + i));
        if (it.label) {
          kids.push(h('div', { key: 'l' + i, className: 'n7k-label' + (it.dim ? ' n7k-label-dim' : ''), style: { left: left, top: y + size + LABEL_TOP, width: size } }, it.label));
        }
      });
      stage(kids);
    },

    /* A real screenshot of the app in a plain rounded frame. A tall one runs off the bottom like the phone photo does. */
    screen: function (tb, stage) {
      var width = 640, border = 2;
      var height = width * page.imageSize[1] / page.imageSize[0] + 2 * border;
      var top = Math.round(tb + 120);
      if (top + height + 60 <= H) top = Math.round(tb + (H - tb - height) / 2);
      report.metrics.screen = { top: top, width: width, height: height };
      stage(h('div', { className: 'n7k-screen', style: { left: (W - width) / 2 - border, top: top, width: width } },
        h('img', { src: page.image, alt: '' })));
    },

    /* How to turn it on: a medium matrix, then numbered lines. The list is measured first because a long step wraps. */
    steps: function (tb, stage) {
      function list(top) {
        return h('div', { key: 'steps', className: 'n7k-steps', style: { top: top } }, page.steps.map(function (text, i) {
          return h('div', { key: i, className: 'n7k-step' },
            h('span', { className: 'n7k-step-n' }, (i < 9 ? '0' : '') + (i + 1)),
            h('span', { className: 'n7k-step-t' }, text));
        }));
      }
      stage(list(tb + MIN_GAP));
      var listH = pic.querySelector('.n7k-steps').getBoundingClientRect().height;
      var gap = 72, room = H - tb;
      var size = Math.max(240, Math.min(440, Math.floor(room - 2 * MIN_GAP - gap - listH)));
      var top = Math.round(tb + Math.max(MIN_GAP, (room - size - gap - listH) / 2));
      var end = top + size + gap + listH;
      report.metrics.steps = { top: top, size: size, listTop: top + size + gap, listHeight: listH };
      // Even with the matrix at its smallest the list would run off the picture: a clipped picture is never written.
      if (end > H - 40) {
        throw new Error('the steps do not fit under the text: with the matrix at its smallest (' + size + 'px) the list ends at y ' +
          Math.ceil(end) + ', ' + Math.ceil(end - (H - 40)) + 'px past the bottom margin of the ' + H + 'px picture. ' +
          'Shorten the headline, the sub line or the steps, or use fewer steps');
      }
      stage([at((W - size) / 2, top, matrix(size, page.cells), 'matrix'), list(top + size + gap)]);
    }
  };
  var LABEL_TOP = 18;   // from the bottom of a grid matrix to the top of its label box (puts the caps 25px under the matrix)

  function shot() {
    function stage(children) {
      draw(h(N7.StoreShot, { width: W, headline: page.headline, sub: page.sub || undefined }, children));
    }
    stage(null);
    fitHeadline('.n7-shot-hl', HEAD_RIGHT - SIDE);
    var text = pic.querySelector('.n7-shot-sub') || pic.querySelector('.n7-shot-hl');
    var tb = text.getBoundingClientRect().bottom;
    report.metrics.textBottom = tb;
    if (!LAYOUTS[page.layout]) throw new Error('unknown layout "' + page.layout + '"');
    LAYOUTS[page.layout](tb, stage);
  }

  /* Feature graphic 1024x500: signature, title in dot type with red stops, sub line; the phone cropped on the right. */
  function feature() {
    draw(h('div', { className: 'n7k-feature' },
      at(540, 40, h(N7.PhoneBack, { width: 460, cells: page.cells, src: page.phone }), 'phone'),
      h('div', { key: 'text', className: 'n7k-feature-text' },
        h(N7.SignatureLabel, null),
        h(N7.DotHeadline, { lines: page.title, className: 'n7k-feature-hl', as: 'div' }),
        page.sub ? h('div', { className: 'n7k-feature-sub' }, page.sub) : null)));
    var text = pic.querySelector('.n7k-feature-text');
    fitHeadline('.n7k-feature-hl', 540 - 64 - 24, function () { return text.getBoundingClientRect().bottom > 500 - 40; });
    var bottom = text.getBoundingClientRect().bottom;
    report.metrics.textBottom = bottom;
    // The title is already shrunk as far as it goes: text past the bottom margin would be cut off, so the picture fails.
    if (bottom > 500 - 24) {
      throw new Error('the feature text does not fit in the 500px picture: with the title shrunk to ' + report.metrics.headlinePx +
        'px it ends at y ' + Math.ceil(bottom) + ', ' + Math.ceil(bottom - (500 - 24)) + 'px past the bottom margin. ' +
        'Shorten the sub line or the title');
    }
  }

  Promise.resolve()
    .then(loadFonts)
    .then(function () { return Promise.all((page.preload || []).map(loadImage)); })
    .then(function () {
      var kinds = { shot: shot, feature: feature };   // the Play icon is drawn in Python (storekit/icon.py)
      if (!kinds[page.kind]) throw new Error('unknown page kind "' + page.kind + '"');
      kinds[page.kind]();
    })
    .then(function () {
      return Promise.all(Array.prototype.map.call(pic.querySelectorAll('img'), function (img) {
        return img.decode().catch(function () { throw new Error('the image did not load: ' + img.src); });
      }));
    })
    .then(function () { return new Promise(function (done) { requestAnimationFrame(function () { requestAnimationFrame(done); }); }); })
    .then(function () { send(null); }, send);
})();
