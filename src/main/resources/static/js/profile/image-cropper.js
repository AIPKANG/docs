/*
 * 프로필 사진 자르기(11 §4-1): 파일 선택(jpg·png·gif·webp, 10MB 이하) → 끌어서 위치·확대 → 정사각형 → 256×256.
 * 캔버스로 다시 그리므로 EXIF(촬영 위치)가 남지 않고, GIF·움직이는 WebP는 첫 장면만 남는다.
 * 전역 BlogCropper.open(file) → Promise<HTMLCanvasElement(256×256)> (취소하면 reject)
 */
(function () {
  'use strict';

  var TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  var MAX_BYTES = 10 * 1024 * 1024;
  var OUTPUT = 256;

  function loadImage(file) {
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () { resolve({ img: img, url: url }); };
      img.onerror = function () { URL.revokeObjectURL(url); reject(new Error('사진을 읽을 수 없어요')); };
      img.src = url;
    });
  }

  /** 가운데 정사각형을 256×256으로(소셜 사진 복사에도 쓴다). */
  function centerSquare(source, width, height) {
    var side = Math.min(width, height);
    var canvas = document.createElement('canvas');
    canvas.width = OUTPUT; canvas.height = OUTPUT;
    canvas.getContext('2d').drawImage(source, (width - side) / 2, (height - side) / 2, side, side, 0, 0, OUTPUT, OUTPUT);
    return canvas;
  }

  function open(file) {
    var box = document.getElementById('cropper');
    var view = document.getElementById('cropper-canvas');
    var zoom = document.getElementById('cropper-zoom');
    var apply = document.getElementById('cropper-apply');
    var cancel = document.getElementById('cropper-cancel');
    var status = document.getElementById('cropper-status');
    if (TYPES.indexOf(file.type) < 0) { return Promise.reject(new Error('jpg·png·gif·webp 사진만 올릴 수 있어요')); }
    if (file.size > MAX_BYTES) { return Promise.reject(new Error('10MB 이하 사진만 올릴 수 있어요')); }

    return loadImage(file).then(function (loaded) {
      var img = loaded.img;
      var size = view.width;
      var base = size / Math.min(img.naturalWidth, img.naturalHeight);
      var state = { scale: 1, x: 0, y: 0 };
      var drag = null;

      function clamp() {
        var w = img.naturalWidth * base * state.scale;
        var h = img.naturalHeight * base * state.scale;
        state.x = Math.min(0, Math.max(size - w, state.x));
        state.y = Math.min(0, Math.max(size - h, state.y));
      }
      function draw() {
        clamp();
        var ctx = view.getContext('2d');
        ctx.clearRect(0, 0, size, size);
        ctx.drawImage(img, state.x, state.y, img.naturalWidth * base * state.scale, img.naturalHeight * base * state.scale);
      }
      state.x = (size - img.naturalWidth * base) / 2;
      state.y = (size - img.naturalHeight * base) / 2;
      zoom.value = '1';
      box.hidden = false;
      status.textContent = '';
      draw();

      function onDown(e) { drag = { px: e.clientX, py: e.clientY, x: state.x, y: state.y }; view.setPointerCapture(e.pointerId); }
      function onMove(e) {
        if (!drag) { return; }
        var ratio = size / view.getBoundingClientRect().width;
        state.x = drag.x + (e.clientX - drag.px) * ratio;
        state.y = drag.y + (e.clientY - drag.py) * ratio;
        draw();
      }
      function onUp() { drag = null; }
      function onZoom() {
        var center = size / 2;
        var next = parseFloat(zoom.value);
        state.x = center - (center - state.x) * (next / state.scale);
        state.y = center - (center - state.y) * (next / state.scale);
        state.scale = next;
        draw();
      }
      view.addEventListener('pointerdown', onDown);
      view.addEventListener('pointermove', onMove);
      view.addEventListener('pointerup', onUp);
      zoom.addEventListener('input', onZoom);

      return new Promise(function (resolve, reject) {
        function cleanup() {
          view.removeEventListener('pointerdown', onDown);
          view.removeEventListener('pointermove', onMove);
          view.removeEventListener('pointerup', onUp);
          zoom.removeEventListener('input', onZoom);
          apply.onclick = null; cancel.onclick = null;
          URL.revokeObjectURL(loaded.url);
          box.hidden = true;
        }
        apply.onclick = function () {
          var out = document.createElement('canvas');
          out.width = OUTPUT; out.height = OUTPUT;
          var k = OUTPUT / size;
          out.getContext('2d').drawImage(img, state.x * k, state.y * k,
            img.naturalWidth * base * state.scale * k, img.naturalHeight * base * state.scale * k);
          cleanup();
          resolve(out);
        };
        cancel.onclick = function () { cleanup(); reject(new Error('cancelled')); };
      });
    });
  }

  window.BlogCropper = { open: open, centerSquare: centerSquare, OUTPUT: OUTPUT };
})();
