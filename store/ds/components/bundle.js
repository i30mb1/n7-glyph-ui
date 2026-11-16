/* @ds-bundle: {"format":4,"namespace":"N7Glyph","components":[{"name":"GlyphMatrix"},{"name":"AppIcon"},{"name":"PhoneBack"},{"name":"DotHeadline"},{"name":"SignatureLabel"},{"name":"Button"},{"name":"ScoreDisplay"},{"name":"ToyCard"},{"name":"StoreShot"}]} */
(function () {
  var React = window.React;
  var h = React.createElement;

  // Visible x-range per row of the Phone (3) Glyph Matrix (measured on the device).
  var BOUNDS = [[9, 15], [7, 17], [5, 19], [4, 20], [3, 21], [2, 22], [2, 22], [1, 23], [1, 23],
    [0, 24], [0, 24], [0, 24], [0, 24], [0, 24], [0, 24], [0, 24],
    [1, 23], [1, 23], [2, 22], [2, 22], [3, 21], [4, 20], [5, 19], [7, 17], [9, 15]];
  function isVisible(x, y) { var b = BOUNDS[y]; return !!b && x >= b[0] && x <= b[1]; }

  // Device photo (user's own Phone (3), white). Lens centre and radius as fractions of the image width/height.
  var PHONE = { src: '/_blob/97bc9485ff70c96c6f9941dceeaa0deb', w: 1240, h: 2684, cx: 0.7968, cy: 0.0879, r: 0.1387 };

  function line(x0, y0, x1, y1) {
    var out = [], dx = Math.sign(x1 - x0), dy = Math.sign(y1 - y0), x = x0, y = y0;
    out.push([x, y]);
    while (x !== x1 || y !== y1) { if (x !== x1) x += dx; else y += dy; out.push([x, y]); }
    return out;
  }
  function snake(points) {
    // points: path from tail to head; returns cells with a brightness ramp (head brightest)
    var n = points.length;
    return points.map(function (p, i) { return [p[0], p[1], i === n - 1 ? 1 : 0.45 + 0.35 * (i / n)]; });
  }
  var frames = {
    snakeIcon: { cells: snake(line(9, 8, 15, 8).concat(line(15, 9, 15, 13))), signal: [[18, 16]] },
    snakePlay: { cells: snake(line(6, 14, 10, 14).concat(line(10, 13, 10, 11)).concat(line(11, 11, 15, 11)).concat(line(15, 10, 15, 9))).concat([[15, 5, 1]]) },
    flappyIcon: (function () {
      var c = [];
      for (var y = 0; y < 25; y++) {
        if (y < 7 || y > 16) { c.push([4, y, 0.5], [5, y, 0.5]); }
        if (y < 4 || y > 13) { c.push([18, y, 0.5], [19, y, 0.5]); }
      }
      return { cells: c, signal: [[11, 11], [12, 11], [11, 12], [12, 12]] };
    })()
  };

  function GlyphMatrix(props) {
    var size = props.size || 200;
    var cells = props.cells || [];
    var signal = props.signal || [];
    var lens = props.lens !== false;
    var glow = props.glow !== false;
    var square = props.led === 'square';
    var rawId = React.useId ? React.useId() : String(Math.random());
    var fid = 'n7g' + rawId.replace(/[^A-Za-z0-9]/g, '');
    var lit = {}, sig = {};
    cells.forEach(function (c) { lit[c[0] + ',' + c[1]] = c[2] == null ? 1 : c[2]; });
    signal.forEach(function (c) { sig[c[0] + ',' + c[1]] = 1; });
    var r = 0.38, kids = [], glows = [];
    for (var y = 0; y < 25; y++) {
      for (var x = 0; x < 25; x++) {
        if (!isVisible(x, y)) continue;
        var k = x + ',' + y, cx = x + 0.5, cy = y + 0.5;
        var style;
        if (sig[k]) style = { fill: 'var(--signal)' };
        else if (lit[k] != null) style = { fill: 'color-mix(in srgb, var(--led-on) ' + (Math.max(0, Math.min(1, lit[k])) * 100).toFixed(1) + '%, var(--led-off))' };
        else style = { fill: 'var(--led-off)' };
        kids.push(square
          ? h('rect', { key: k, x: cx - r, y: cy - r, width: 2 * r, height: 2 * r, rx: 0.1, style: style })
          : h('circle', { key: k, cx: cx, cy: cy, r: r, style: style }));
        if (glow && lit[k] != null && !sig[k]) glows.push(h('circle', { key: 'g' + k, cx: cx, cy: cy, r: 0.7, style: { fill: 'var(--led-on)', fillOpacity: 0.35 * lit[k] } }));
      }
    }
    var vb = lens ? '-1.6 -1.6 28.2 28.2' : '0 0 25 25';
    return h('svg', { width: size, height: size, viewBox: vb, role: 'img', 'aria-label': props.label || 'Glyph Matrix', style: { display: 'block' } },
      glow ? h('defs', null, h('filter', { id: fid, x: '-50%', y: '-50%', width: '200%', height: '200%' }, h('feGaussianBlur', { stdDeviation: 0.55 }))) : null,
      lens ? h('circle', { cx: 12.5, cy: 12.5, r: 13.85, style: { fill: 'var(--lens)', stroke: 'var(--lens-rim)', strokeWidth: 0.22 } }) : null,
      h('g', null, kids),
      glows.length ? h('g', { filter: 'url(#' + fid + ')' }, glows) : null);
  }

  function AppIcon(props) {
    var size = props.size || 96;
    var fill = props.fill || 0.82;
    var radius = props.shape === 'circle' ? '50%' : props.shape === 'square' ? '0' : '22%';
    var m = Math.round(size * fill);
    return h('div', { className: 'n7-icon', style: { width: size, height: size, borderRadius: radius } },
      h('div', { style: { position: 'absolute', left: (size - m) / 2, top: (size - m) / 2 } },
        h(GlyphMatrix, { size: m, cells: props.cells, signal: props.signal, glow: false, label: props.label || 'App icon' })));
  }

  function PhoneBack(props) {
    var width = props.width || 320;
    var src = props.src || PHONE.src;
    var height = width * PHONE.h / PHONE.w;
    var d = 2 * PHONE.r * width;
    return h('div', { style: { position: 'relative', width: width, height: height, transform: props.tilt ? 'rotate(' + props.tilt + 'deg)' : null, flex: 'none' } },
      h('img', { src: src, alt: props.alt || 'Nothing Phone (3), back', style: { width: '100%', height: '100%', display: 'block' } }),
      h('div', { style: { position: 'absolute', left: PHONE.cx * width - d / 2, top: PHONE.cy * height - d / 2 } },
        h(GlyphMatrix, { size: d, cells: props.cells, signal: props.signal })));
  }

  var CYR = /[Ѐ-ӿ]/;
  function DotHeadline(props) {
    var lines = props.lines || [];
    var stops = props.stops !== false;
    var allCyr = lines.some(function (l) { return CYR.test(l); });
    return h(props.as || 'h1', { className: ['n7-hl', allCyr ? 'n7-hl-cyr n7-hl-cyr-all' : 'n7-dot', props.className].filter(Boolean).join(' '), style: Object.assign({ fontSize: props.size }, props.style) },
      lines.map(function (l, i) {
        return h('span', { key: i, className: 'n7-hl-line' }, l, stops ? h('span', { className: 'n7-hl-stop', 'aria-hidden': true }) : null);
      }));
  }

  function SignatureLabel(props) {
    return h('span', { className: 'n7-sig', style: props.style }, h('span', { className: 'n7-sig-dot', 'aria-hidden': true }), props.children || 'Made for Glyph Matrix');
  }

  function Button(props) {
    var variant = props.variant || 'primary';
    var rest = Object.assign({}, props);
    delete rest.variant; delete rest.block;
    rest.className = ['n7-btn', 'n7-btn-' + variant, props.block ? 'n7-btn-block' : null, props.className].filter(Boolean).join(' ');
    return h('button', rest, props.children);
  }

  function ScoreDisplay(props) {
    var digits = props.digits || 3;
    var v = String(props.value == null ? 0 : props.value);
    while (v.length < digits) v = '0' + v;
    return h('div', { className: 'n7-score' },
      h('span', { className: 'n7-score-label' }, props.label || 'Best'),
      h('span', { className: 'n7-score-value n7-dot', style: props.size ? { fontSize: props.size } : null }, v));
  }

  function ToyCard(props) {
    return h('div', { className: 'n7-toy' },
      h(GlyphMatrix, { size: props.matrixSize || 72, cells: props.cells, signal: props.signal, glow: false }),
      h('div', { className: 'n7-toy-text' },
        h('div', { className: 'n7-toy-title' }, props.title),
        props.summary ? h('div', { className: 'n7-toy-sum' }, props.summary) : null),
      props.action || null);
  }

  function StoreShot(props) {
    var width = props.width || 270;
    var s = width / 1080;
    return h('div', { className: 'n7-shot', style: { width: width, height: width * 1920 / 1080 } },
      h('div', { className: 'n7-shot-inner', style: { transform: 'scale(' + s + ')' } },
        h('div', { style: { position: 'absolute', inset: 0 } }, props.children),
        h('div', { style: { position: 'relative' } },
          h(SignatureLabel, null, props.signature),
          h(DotHeadline, { lines: props.headline || [], className: 'n7-shot-hl', as: 'div' }),
          props.sub ? h('div', { className: 'n7-shot-sub' }, props.sub) : null)));
  }

  window.N7Glyph = Object.assign(window.N7Glyph || {}, {
    GlyphMatrix: GlyphMatrix, AppIcon: AppIcon, PhoneBack: PhoneBack, DotHeadline: DotHeadline,
    SignatureLabel: SignatureLabel, Button: Button, ScoreDisplay: ScoreDisplay, ToyCard: ToyCard, StoreShot: StoreShot,
    frames: frames, line: line, snake: snake, isVisible: isVisible, BOUNDS: BOUNDS, PHONE: PHONE
  });
})();
