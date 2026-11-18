// One offline mesh/atlas and one draw call per camera or lighting change.
const ASSETS = '/shared/vehicle/';
const TURN = 2 * Math.PI;
const FOV = Math.tan(Math.PI / 17);
const DEFAULT_YAW = 1.13;
const DEFAULT_PITCH = .084;
// Symmetric silhouette samples: corners, roof, wheels and mirrors. Excludes the floor.
const ENVELOPE = [[-2.226,.32,.76],[-2.20,1.115,.79],[-1.53,1.55,.59],[-.66,1.627,.575],
  [.42,1.53,.57],[.56,1.185,1.02],[1.25,1.04,.86],[2.17,.32,.68],[2.22,.86,.70],[-1.31,0,.85],[1.31,0,.85]];
const clamp = (x, min, max) => Math.max(min, Math.min(max, x));
const wrap = x => x % TURN;

async function asset(name, decode) {
  const response = await fetch(ASSETS + name);
  if (!response.ok) throw new Error(`Missing vehicle asset: ${name}`);
  return response[decode]();
}
function decodeMesh(bytes) {
  if (bytes.byteLength < 16) throw new Error('Invalid vehicle mesh');
  const header = new DataView(bytes);
  const vertices = header.getUint32(8, true);
  const indices = header.getUint32(12, true);
  const end = 16 + vertices * 28;
  if (header.getUint32(0, true) !== 0x44335a56 || header.getUint32(4, true) !== 1 ||
      !vertices || vertices > 65535 || !indices || indices % 3 || end + indices * 2 !== bytes.byteLength) {
    throw new Error('Invalid vehicle mesh');
  }
  return [new Uint8Array(bytes, 16, vertices * 28), new Uint8Array(bytes, end), indices];
}

function shader(gl, type, source) {
  const sh = gl.createShader(type);
  gl.shaderSource(sh, source);
  gl.compileShader(sh);
  if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(sh));
  return sh;
}

async function pipeline(gl, isCurrent) {
  const image = new Image();
  image.src = ASSETS + 'decals.png';
  const [vs, fs, raw] = await Promise.all([
    asset('showroom.vert', 'text'), asset('showroom.frag', 'text'), asset('vezel.mesh', 'arrayBuffer'), image.decode(),
  ]);
  if (!isCurrent() || gl.isContextLost()) return null;
  const [verts, ids, count] = decodeMesh(raw);
  const program = gl.createProgram();
  const vert = shader(gl, gl.VERTEX_SHADER, vs);
  const frag = shader(gl, gl.FRAGMENT_SHADER, fs);
  gl.attachShader(program, vert);
  gl.attachShader(program, frag);
  gl.linkProgram(program);
  gl.deleteShader(vert);
  gl.deleteShader(frag);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program));
  gl.useProgram(program);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER, verts, gl.STATIC_DRAW);
  for (const [name, size, type, normal, offset] of [
    ['aPosition', 3, gl.FLOAT, false, 0], ['aNormal', 3, gl.BYTE, true, 12],
    ['aColor', 4, gl.UNSIGNED_BYTE, true, 16], ['aTex', 2, gl.UNSIGNED_SHORT, true, 20],
    ['aMaterial', 4, gl.UNSIGNED_BYTE, true, 24],
  ]) {
    const i = gl.getAttribLocation(program, name);
    gl.enableVertexAttribArray(i);
    gl.vertexAttribPointer(i, size, type, normal, 28, offset);
  }
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, ids, gl.STATIC_DRAW);
  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, gl.createTexture());
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.generateMipmap(gl.TEXTURE_2D);
  gl.uniform1i(gl.getUniformLocation(program, 'uDecals'), 0);
  gl.enable(gl.DEPTH_TEST);
  if (gl.getError() !== gl.NO_ERROR) throw new Error('Unable to upload the vehicle');
  const uniform = Object.fromEntries(['VP', 'Eye', 'Lights', 'Night'].map(key => [key, gl.getUniformLocation(program, `u${key}`)]));
  return { uniform, count };
}

// Column-major perspective * view matrix, same orbit/projection as VehicleCamera on Android.
export function viewpoint(yaw, pitch, zoom, inspecting, aspect, output) {
  const c = Math.cos(yaw), s = Math.sin(yaw);
  const cp = Math.cos(pitch), sp = Math.sin(pitch);
  let radius = Math.max((inspecting ? 9.4 : 10.25) / zoom,
    Math.abs(c) * 2.23 + Math.abs(s) * .91 +
    (Math.abs(s) * 2.23 + Math.abs(c) * .91) / (FOV * aspect * (inspecting ? .96 : .88) * Math.max(1, zoom / 1.5)));
  const nx = c * cp, ny = sp, nz = s * cp;
  const target = .80 - (inspecting ? .122 * clamp((zoom - 1) * 3, 0, 1) * Math.pow(Math.abs(c), 8) : 0);
  const f = 1 / FOV, fx = f / aspect;
  if (!inspecting) {
    // Reserve space for the route / title above and the buttons below, even when tilted.
    const range = (aspect < 1.6 ? .54 : .635) * Math.max(1, zoom);
    for (const [x, y, halfWidth] of ENVELOPE) for (const side of [-1, 1]) {
      const z = side * halfWidth;
      const up = -c * sp * x + cp * (y - target) - s * sp * z;
      const along = nx * x + ny * (y - target) + nz * z;
      radius = Math.max(radius, along + f * Math.abs(up) / range);
    }
  }
  const y = sp * radius + target;
  const near = .23, far = 48;
  const depth = -(far + near) / (far - near);
  const shift = inspecting ? .035 : .104;
  const z = -radius - sp * target;
  for (const [i, right, up, forward] of [[0, s, -c * sp, nx], [4, 0, cp, ny], [8, -c, -s * sp, nz], [12, 0, -cp * target, z]]) {
    output[i] = fx * right;
    output[i+1] = f * up + shift * forward;
    output[i+2] = depth * forward - (i === 12 ? 2 * near * far / (far - near) : 0);
    output[i+3] = -forward;
  }
  return [nx * radius, y, nz * radius];
}

export function attachShowroom() {
  const tile = document.querySelector('.vehicle');
  const canvas = document.getElementById('vehicle-model');
  const expand = document.getElementById('vehicle-expand');
  const dialog = document.getElementById('vehicle-inspector');
  const studio = document.getElementById('vehicle-studio');
  const controls = document.getElementById('vehicle-controls');
  const close = document.getElementById('vehicle-close');
  let gl, scene;
  let yaw = DEFAULT_YAW, pitch = DEFAULT_PITCH, magnification = 1;
  let lamps = false, night = false, inspecting = false, initializing = 0, request = 0;
  const matrix = new Float32Array(16);
  const pointers = new Map();

  function requestDraw() {
    if (scene && !request && !document.hidden) request = requestAnimationFrame(draw);
  }
  function draw() {
    request = 0;
    if (!scene || gl.isContextLost()) return;
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) return;
    const scale = Math.min(1, 960 / w, 560 / h);
    const width = Math.max(1, Math.round(w * scale)), height = Math.max(1, Math.round(h * scale));
    if (canvas.width !== width) canvas.width = width;
    if (canvas.height !== height) canvas.height = height;
    const [x, y, z] = viewpoint(yaw, pitch, magnification, inspecting, w / h, matrix);
    gl.viewport(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight);
    const dim = night ? .61 : 1;
    gl.clearColor(.085 * dim, .072 * dim, .087 * dim, 1);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    const u = scene.uniform;
    gl.uniformMatrix4fv(u.VP, false, matrix);
    gl.uniform3f(u.Eye, x, y, z);
    gl.uniform1f(u.Lights, Number(lamps));
    gl.uniform1f(u.Night, Number(night));
    gl.drawElements(gl.TRIANGLES, scene.count, gl.UNSIGNED_SHORT, 0);
    if (!tile.classList.contains('model-ready')) {
      tile.classList.add('model-ready');
      expand.hidden = false;
      canvas.tabIndex = 0;
      canvas.setAttribute('aria-hidden', 'false');
    }
  }
  function endDrag() {
    pointers.clear();
    canvas.classList.remove('rotating');
  }
  function fallback() {
    scene = null;
    endDrag();
    if (request) cancelAnimationFrame(request);
    request = 0;
    tile.classList.remove('model-ready');
    expand.hidden = true;
    canvas.tabIndex = -1;
    canvas.setAttribute('aria-hidden', 'true');
    closeInspection();
  }
  async function initialize() {
    const epoch = ++initializing;
    try {
      gl = canvas.getContext('webgl', { alpha: false, depth: true, stencil: false, antialias: true });
      if (!gl) return;
      const loaded = await pipeline(gl, () => epoch === initializing);
      if (!loaded || epoch !== initializing || gl.isContextLost()) return;
      scene = loaded;
      requestDraw();
    } catch (error) {
      if (epoch !== initializing) return;
      fallback();
      console.warn('Using the static Vezel illustration:', error.message);
    }
  }
  function preset(action) {
    zoom(action === 'reset' ? 1 : 1.32);
    yaw = action === 'rear' ? Math.PI : action === 'front' ? 0 : DEFAULT_YAW;
    pitch = action === 'reset' ? DEFAULT_PITCH : .1;
    requestDraw();
  }
  function rotate(dx, dy) {
    yaw = wrap(yaw - dx * TURN / Math.max(1, canvas.getBoundingClientRect().width));
    pitch = clamp(pitch + dy * .009, .065, 1.42);
    requestDraw();
  }
  function zoom(value) {
    magnification = clamp(value, .84, 2.4);
    requestDraw();
  }
  canvas.addEventListener('pointerdown', event => {
    if (!scene || event.button !== 0 || pointers.size >= 2) return;
    canvas.setPointerCapture(event.pointerId);
    pointers.set(event.pointerId, [event.clientX, event.clientY]);
    canvas.classList.add('rotating');
  });
  canvas.addEventListener('pointermove', event => {
    if (!pointers.has(event.pointerId)) return;
    const [x, y] = pointers.get(event.pointerId);
    const next = [event.clientX, event.clientY];
    if (pointers.size === 1) rotate(next[0] - x, next[1] - y);
    else {
      const [ox, oy] = [...pointers].find(([id]) => id !== event.pointerId)[1];
      const before = Math.hypot(x - ox, y - oy), after = Math.hypot(next[0] - ox, next[1] - oy);
      if (before > 8 && after > 8) zoom(magnification * after / before);
    }
    pointers.set(event.pointerId, next);
  });
  for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) canvas.addEventListener(type, event => {
    pointers.delete(event.pointerId);
    if (!pointers.size) canvas.classList.remove('rotating');
  });
  canvas.addEventListener('wheel', event => {
    if (!scene || (!inspecting && document.activeElement !== canvas)) return;
    event.preventDefault();
    zoom(magnification * Math.exp(-clamp(event.deltaY, -120, 120) * .0025));
  }, { passive: false });
  canvas.addEventListener('dblclick', () => preset('reset'));
  canvas.addEventListener('keydown', event => {
    if (!scene) return;
    const step = canvas.getBoundingClientRect().width / 24;
    switch (event.key) {
      case 'ArrowLeft': rotate(-step, 0); break;
      case 'ArrowRight': rotate(step, 0); break;
      case 'ArrowUp': rotate(0, 16); break;
      case 'ArrowDown': rotate(0, -16); break;
      case '+': case '=': zoom(magnification * 1.14); break;
      case '-': zoom(magnification / 1.14); break;
      case 'Home': preset('reset'); break;
      default: return;
    }
    event.preventDefault();
  });
  expand.addEventListener('click', () => {
    if (!scene || dialog.open) return;
    inspecting = true;
    magnification = 1;
    studio.append(canvas);
    dialog.showModal();
    close.focus();
    requestDraw();
  });
  function closeInspection() {
    if (!dialog.open) return;
    dialog.close();
    leaveInspection();
  }
  close.addEventListener('click', closeInspection);
  controls.addEventListener('click', event => {
    const button = event.target.closest('[data-vehicle-view]');
    if (button) preset(button.dataset.vehicleView);
  });
  function leaveInspection() {
    if (!inspecting) return;
    endDrag();
    inspecting = false;
    magnification = 1;
    tile.insertBefore(canvas, tile.querySelector('.vehicle-shade'));
    if (scene) expand.focus();
    requestDraw();
  }
  dialog.addEventListener('close', leaveInspection);
  dialog.addEventListener('cancel', event => {
    event.preventDefault();
    closeInspection();
  });
  canvas.addEventListener('webglcontextlost', event => {
    event.preventDefault();
    ++initializing;
    fallback();
  });
  canvas.addEventListener('webglcontextrestored', initialize);
  if (typeof ResizeObserver !== 'undefined') {
    const observer = new ResizeObserver(requestDraw);
    observer.observe(tile);
    observer.observe(studio);
  }
  window.addEventListener('resize', requestDraw);
  window.addEventListener('blur', endDrag);
  document.addEventListener('visibilitychange', requestDraw);
  initialize();
  return (lights, dim) => {
    if (lamps === lights && night === dim) return;
    lamps = lights;
    night = dim;
    dialog.classList.toggle('night-mode', night);
    requestDraw();
  };
}
