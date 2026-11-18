const $ = (id) => document.getElementById(id);
const consoleView = $('console');
const panel = $('panel');
const body = $('panel-content');
const icon = (name) => `<svg class="icon" aria-hidden="true"><use href="/icons.svg#${name}"></use></svg>`;
const storageKey = 'vezel-simulator-v1';
const apkUrl = '/download/Vezel-Drive-Console.apk';
let apkAvailable = false;
function updateApkLinks() {
  document.querySelectorAll('[data-apk]').forEach(link => { link.hidden = !apkAvailable; });
  if ($('apk-missing')) $('apk-missing').hidden = apkAvailable;
}
async function checkApk() {
  try { apkAvailable = (await fetch(apkUrl, { method: 'HEAD', cache: 'no-store' })).ok; }
  catch { apkAvailable = false; }
  updateApkLinks();
}
const tracks = [
  { name: 'Night Drive', artist: 'Vezel Sessions', notes: [52, 59, 64, 67, 55, 62, 64, 71], hue: 0 },
  { name: 'Colombo After Dark', artist: 'Vezel Sessions', notes: [48, 55, 60, 64, 50, 57, 62, 65], hue: 287 },
  { name: 'Coastal Air', artist: 'Vezel Sessions', notes: [50, 57, 62, 66, 54, 61, 66, 69], hue: 145 },
];
const normalizeRegistration = value => {
  if (typeof value !== 'string' || value.length > 64) return '';
  const label = value.trim().toUpperCase().replace(/\s+/g, ' ');
  return /^(?:[A-Z]{2} )?[A-Z0-9]{1,6} [A-Z0-9]{1,6}$/.test(label) ? label : '';
};
const config = { theme: 'auto', lampMode: 'auto', gaugeStyle: 'dial', registration: '', limit: 60, alert: true, powerTones: true, track: 0 };
try {
  const saved = JSON.parse(localStorage.getItem(storageKey));
  if (saved && typeof saved === 'object') {
    if (['auto', 'day', 'night'].includes(saved.theme)) config.theme = saved.theme;
    if (['auto', 'on', 'off'].includes(saved.lampMode)) config.lampMode = saved.lampMode;
    else if (saved.lamps === true) config.lampMode = 'on';
    if (saved.gaugeStyle === 'large') config.gaugeStyle = 'large';
    config.registration = normalizeRegistration(saved.registration);
    if (typeof saved.alert === 'boolean') config.alert = saved.alert;
    if (typeof saved.powerTones === 'boolean') config.powerTones = saved.powerTones;
    if (Number.isInteger(saved.limit) && saved.limit >= 20 && saved.limit <= 140) config.limit = saved.limit;
    if (Number.isInteger(saved.track) && tracks[saved.track]) config.track = saved.track;
  }
} catch { /* The preview also works with storage disabled. */ }
const save = () => { try { localStorage.setItem(storageKey, JSON.stringify(config)); } catch {} };
let demoHeadlights = false; // A browser sample, never a reading from the vehicle.
let demoSpeed = 50;
let trip = 0.4;
let drive = 21 * 60 + 51;
let exceeded = false;
let snackbarTimer;
let welcomeTimer;

function sizeConsole() {
  const { clientWidth, clientHeight } = document.documentElement;
  consoleView.style.setProperty('--scale', String(Math.min(clientWidth / 1024, clientHeight / 600)));
}
window.addEventListener('resize', sizeConsole);
sizeConsole();

const clockFormat = new Intl.DateTimeFormat('en-LK', { hour: 'numeric', minute: '2-digit', hour12: true, timeZone: 'Asia/Colombo' });
const dateFormat = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'Asia/Colombo' });
function clock() {
  const now = new Date();
  const parts = Object.fromEntries(clockFormat.formatToParts(now).map(({ type, value }) => [type, value]));
  $('time').textContent = `${parts.hour}:${parts.minute}`;
  $('time').dateTime = now.toISOString();
  $('period').textContent = parts.dayPeriod.toUpperCase();
  $('date').textContent = dateFormat.format(now).toUpperCase();
}
let lastTick = Date.now();
setInterval(() => {
  clock();
  const now = Date.now();
  if (!document.hidden && demoSpeed > 0 && now - lastTick < 2200) {
    drive += (now - lastTick) / 1000;
    trip += demoSpeed * (now - lastTick) / 3_600_000;
    renderTrip();
  }
  lastTick = now;
}, 1000);
clock();

// Keep the progress arc and needle on the same scale as the two static dial faces.
const gaugeMax = 180, start = 138, sweep = 264;
const polar = (r, degrees) => [114 + Math.cos(degrees * Math.PI / 180) * r, 114 + Math.sin(degrees * Math.PI / 180) * r];
const arc = end => `M${polar(98, start).join(' ')} A98 98 0 ${end - start > 180 ? 1 : 0} 1 ${polar(98, end).join(' ')}`;
const rail = Array.from({ length: 27 }, () => $('speed-rail').appendChild(document.createElement('i')));
function renderGauge(allowVoice = false) {
  const progress = Math.max(0, Math.min(demoSpeed / gaugeMax, 1));
  const angle = start + sweep * progress;
  $('dial-arc').setAttribute('d', progress ? arc(angle) : '');
  $('dial-needle').setAttribute('transform', `translate(114 114) rotate(${angle})`);
  rail.forEach((segment, i) => segment.classList.toggle('reached', i < Math.round(progress * rail.length)));
  $('speed').classList.toggle('three-digits', demoSpeed >= 100);
  $('speed').textContent = demoSpeed;
  $('limit-sign').textContent = config.alert ? config.limit : '–';
  const over = config.alert && demoSpeed >= config.limit;
  consoleView.classList.toggle('over-limit', over);
  $('limit-warning').hidden = !over;
  $('limit-normal').hidden = over;
  $('warning-detail').textContent = `${config.limit} km/h · Slow down`;
  if (allowVoice && over && !exceeded) {
    playCue('speed-warning.mp3', .82);
    toast(`Speed limit reached · ${config.limit} km/h`);
  }
  if (over) exceeded = true;
  else if (demoSpeed <= config.limit - 4 || !config.alert) exceeded = false;
}
function renderTrip() {
  $('trip').textContent = trip.toFixed(1);
  const minutes = Math.floor(drive / 60);
  $('drive').textContent = minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${String(minutes).padStart(2, '0')}:${String(Math.floor(drive % 60)).padStart(2, '0')}`;
}
function renderAppearance() {
  const isNight = config.theme === 'night' || (config.theme === 'auto' && demoHeadlights);
  const lamps = config.lampMode === 'on' || (config.lampMode === 'auto' && demoHeadlights);
  document.documentElement.dataset.palette = isNight ? 'night' : 'day';
  document.querySelector('meta[name=theme-color]').content = isNight ? '#101017' : '#eee5da';
  $('vehicle-mode').textContent = isNight ? 'NIGHT MODE' : 'DAYLIGHT';
  consoleView.classList.toggle('lights-on', lamps);
  consoleView.classList.toggle('dial-gauge', config.gaugeStyle === 'dial');
  $('registration-label').textContent = config.registration;
  $('registration-badge').hidden = !config.registration;
  $('photo-edition').hidden = !!config.registration;
  $('night').setAttribute('aria-pressed', String(isNight));
  const nextDisplay = isNight ? 'Switch to day display' : 'Switch to night display';
  $('night').title = nextDisplay;
  $('night').setAttribute('aria-label', nextDisplay);
  $('night').querySelector('use').setAttribute('href', `/icons.svg#${isNight ? 'sun' : 'moon'}`);
  $('lamps').setAttribute('aria-pressed', String(lamps));
  $('light-label').textContent = lamps ? 'LIGHTS + FOG ON' : 'LIGHTS + FOG OFF';
  $('demo-lights').setAttribute('aria-pressed', String(demoHeadlights));
  $('demo-lights-label').textContent = `DEMO · LIGHTS ${demoHeadlights ? 'ON' : 'OFF'}`;
  renderAppearanceButtons();
}
function renderAppearanceButtons() {
  body.querySelectorAll('[data-theme]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.theme === config.theme)));
  body.querySelectorAll('[data-lamp-mode]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.lampMode === config.lampMode)));
  body.querySelectorAll('[data-gauge]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.gauge === config.gaugeStyle)));
}
function toast(message) {
  const bar = $('snackbar');
  bar.textContent = message;
  bar.hidden = false;
  clearTimeout(snackbarTimer);
  snackbarTimer = setTimeout(() => { bar.hidden = true; }, 2600);
}
const dashboardNodes = [...consoleView.querySelectorAll('.topbar,.main-panels,.dock-row,.console-footer')];
function syncDashboardInput() {
  const blocked = !$('apps-page').hidden || !$('welcome').hidden;
  dashboardNodes.forEach(node => { node.inert = blocked; });
}
function greeting(goodbye = false) {
  if (panel.open) panel.close();
  clearTimeout(snackbarTimer);
  $('snackbar').hidden = true;
  const element = $('welcome');
  clearTimeout(welcomeTimer);
  element.classList.remove('active');
  element.classList.toggle('leaving', goodbye);
  $('greeting').textContent = goodbye ? 'GOOD BYE' : 'Welcome';
  element.hidden = false;
  void element.offsetWidth;
  element.classList.add('active');
  syncDashboardInput();
  $('apps-page').inert = true;
  if (config.powerTones) playCue(goodbye ? 'goodbye.wav' : 'welcome.wav', .80);
  welcomeTimer = setTimeout(() => {
    element.hidden = true;
    element.classList.remove('active');
    $('apps-page').inert = false;
    syncDashboardInput();
  }, goodbye ? 2880 : 2600);
}
$('brand').addEventListener('click', () => greeting());
$('night').addEventListener('click', () => {
  config.theme = document.documentElement.dataset.palette === 'night' ? 'day' : 'night';
  save(); renderAppearance();
});
$('lamps').addEventListener('click', () => {
  config.lampMode = consoleView.classList.contains('lights-on') ? 'off' : 'on';
  save(); renderAppearance();
});
function simulateHeadlights(on) {
  demoHeadlights = on;
  renderAppearance();
}
$('demo-lights').addEventListener('click', () => simulateHeadlights(!demoHeadlights));

// Tiny original soft-synth loops, generated only if the visitor chooses to play.
let context;
let activeSource;
let musicGain;
let loopStart = 0;
let loopOffset = 0;
let playRequest = 0;
const musicBuffers = new Map();
function audio() {
  const Audio = window.AudioContext || window.webkitAudioContext;
  if (!Audio) return null;
  if (!context) context = new Audio();
  return context;
}
const cue = new Audio();
cue.preload = 'none';
let cueRequest = 0;
function duckMusic(target) {
  if (musicGain) musicGain.gain.setTargetAtTime(target, context.currentTime, .045);
}
function playCue(name, level) {
  const request = ++cueRequest;
  cue.pause();
  cue.src = `/shared/audio/${name}`;
  cue.volume = level;
  duckMusic(.25);
  cue.play().catch(() => { if (request === cueRequest) duckMusic(1); });
}
cue.addEventListener('ended', () => duckMusic(1));
cue.addEventListener('error', () => duckMusic(1));

function loop(ctx, index) {
  if (musicBuffers.has(index)) return musicBuffers.get(index);
  const sampleRate = 22050;
  const step = .48;
  const length = Math.round(sampleRate * step * tracks[index].notes.length);
  const buffer = ctx.createBuffer(1, length, sampleRate);
  const data = buffer.getChannelData(0);
  for (const [j, note] of tracks[index].notes.entries()) {
    const hz = 440 * 2 ** ((note - 69) / 12);
    for (let i = 0; i < sampleRate * 1.4; i++) {
      const t = i / sampleRate;
      const phase = 2 * Math.PI * hz * t;
      const env = Math.min(t * 70, 1) * Math.exp(-3.3 * t) * Math.min((1.4 - t) * 9, 1);
      const p = (Math.round(j * step * sampleRate) + i) % length;
      data[p] += env * (.085 * Math.sin(phase) + .032 * Math.sin(2 * phase) + .022 * Math.sin(phase / 2));
    }
  }
  musicBuffers.set(index, buffer);
  return buffer;
}
async function startMusic() {
  const request = ++playRequest;
  try {
    const ctx = audio();
    if (!ctx) throw new Error('Audio unavailable');
    await ctx.resume();
    if (request !== playRequest || activeSource) return;
    const source = ctx.createBufferSource();
    source.buffer = loop(ctx, config.track);
    source.loop = true;
    if (!musicGain) {
      musicGain = ctx.createGain();
      musicGain.gain.value = cue.paused ? 1 : .25;
      musicGain.connect(ctx.destination);
    }
    source.connect(musicGain);
    loopStart = ctx.currentTime;
    source.start(loopStart, loopOffset);
    activeSource = source;
    renderTrack();
  } catch { toast('Audio is not available in this browser.'); }
}
function stopMusic(rememberOffset = true) {
  ++playRequest;
  if (activeSource) {
    if (rememberOffset) loopOffset = (loopOffset + context.currentTime - loopStart) % activeSource.buffer.duration;
    activeSource.stop(); activeSource.disconnect(); activeSource = null;
  }
  if (!rememberOffset) loopOffset = 0;
  renderTrack();
}
function toggleMusic() { if (activeSource) stopMusic(); else startMusic(); }
function chooseTrack(index, forcePlay = !!activeSource) {
  stopMusic(false);
  config.track = (index + tracks.length) % tracks.length;
  save(); renderTrack();
  if (forcePlay) startMusic();
}
function renderTrack() {
  const track = tracks[config.track];
  $('track').textContent = track.name;
  $('artist').textContent = track.artist;
  $('art').style.filter = `hue-rotate(${track.hue}deg)`;
  $('play-icon').setAttribute('href', `/icons.svg#${activeSource ? 'pause' : 'play'}`);
  $('play').setAttribute('aria-label', activeSource ? 'Pause' : 'Play');
  body.querySelectorAll('[data-track]').forEach(button => button.setAttribute('aria-current', String(Number(button.dataset.track) === config.track)));
  const playButton = body.querySelector('[data-action="play"]');
  if (playButton) playButton.textContent = activeSource ? 'Pause preview' : 'Play preview';
}
$('play').addEventListener('click', toggleMusic);
$('previous').addEventListener('click', () => chooseTrack(config.track - 1));
$('next').addEventListener('click', () => chooseTrack(config.track + 1));

const panels = {
  settings: ['SIMULATOR CONTROLS', 'Drive settings'],
  lighting: ['FACTORY DIMMER / BROWSER DEMO', 'Headlight sync'],
  'bt-audio': ['ANDROID SHORTCUT', 'BT Audio'],
  music: ['VEZEL SESSIONS', 'Now playing'],
  phone: ['ANDROID SHORTCUT', 'Phone'],
  radio: ['ANDROID SHORTCUT', 'Radio'],
  weather: ['SIMULATED WEATHER', 'Colombo'],
};
function show(name) {
  if (name === 'apps') { openApps(); return; }
  if (!(name in panels)) return;
  $('panel-kicker').textContent = panels[name][0];
  $('panel-title').textContent = panels[name][1];
  switch (name) {
    case 'settings':
      body.innerHTML = `<p class="panel-note">Try the console here with sample data. The Android app uses your head unit’s GPS, notifications and installed apps.</p>
        <button class="lighting-card" type="button" data-panel="lighting">${icon('headlights')}<span><small class="eyebrow">FACTORY DIMMER</small><b>Headlight sync</b></span>${icon('chevron')}</button>
        <section class="panel-section"><div class="setting-caption">Speed display</div><div class="segmented" aria-label="Speed display">${[['large', 'Large number'], ['dial', 'Metallic dial']].map(([value, label]) => `<button type="button" data-gauge="${value}" aria-pressed="${config.gaugeStyle === value}">${label}</button>`).join('')}</div></section>
        <form class="panel-section" id="registration-form"><label for="registration-input">Registration label (optional)</label><div class="registration-field"><input type="text" id="registration-input" maxlength="32" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="e.g. WP ABC 0123"><button type="submit" class="action">Save</button></div></form>
        <section class="panel-section"><div class="setting-caption">Dashboard colors</div><div class="segmented" aria-label="Dashboard colors">${['auto', 'day', 'night'].map(mode => `<button type="button" data-theme="${mode}" aria-pressed="${config.theme === mode}">${mode.charAt(0).toUpperCase() + mode.slice(1)}</button>`).join('')}</div></section>
        <section class="panel-section"><div class="setting-caption">Lights on the car photo</div><div class="segmented" aria-label="Photo lights">${['auto', 'on', 'off'].map(mode => `<button type="button" data-lamp-mode="${mode}" aria-pressed="${config.lampMode === mode}">${mode.charAt(0).toUpperCase() + mode.slice(1)}</button>`).join('')}</div></section>
        <section class="panel-section"><label for="limit">Speed alert <output id="limit-value">${config.limit} km/h</output></label><input type="range" id="limit" min="20" max="140" value="${config.limit}"><label class="check-row" for="alert">Speak when the limit is reached<input type="checkbox" id="alert" ${config.alert ? 'checked' : ''}></label></section>
        <section class="panel-section"><label for="demo-speed">Simulated GPS speed <output id="speed-value">${demoSpeed} km/h</output></label><input type="range" id="demo-speed" min="0" max="${gaugeMax}" value="${demoSpeed}"></section>
        <div class="actions"><button type="button" class="action" data-action="warning">Preview warning voice</button><button type="button" class="action" data-action="reset">Reset trip</button><a id="apk" class="action primary" href="${apkUrl}" data-apk download hidden>${icon('download')} Android APK</a></div><section class="panel-section"><div class="setting-caption">Power cues</div><label class="check-row" for="power-tones">Welcome and Good Bye tones<input id="power-tones" type="checkbox" ${config.powerTones ? 'checked' : ''}></label><div class="actions"><button type="button" class="action" data-action="startup">Preview Welcome</button><button type="button" class="action" data-action="goodbye">Preview Good Bye</button></div><p class="panel-note">These previews don’t change Android power. Automatic key-off depends on the head unit.</p></section><p id="apk-missing" class="panel-note" hidden>The APK is not available yet. Build it from the Android project to enable the download.</p>`;
      updateApkLinks();
      checkApk();
      const registration = $('registration-input');
      registration.value = config.registration;
      registration.addEventListener('input', () => registration.setCustomValidity(''));
      $('registration-form').addEventListener('submit', event => {
        event.preventDefault();
        const label = normalizeRegistration(registration.value);
        if (registration.value.trim() && !label) {
          registration.setCustomValidity('Use two blocks and an optional province, e.g. WP ABC 0123.');
          registration.reportValidity();
          return;
        }
        config.registration = label;
        registration.value = label;
        save(); renderAppearance(); toast(label ? 'Registration label saved.' : 'Registration label hidden.');
      });
      $('limit').addEventListener('input', e => { config.limit = Number(e.target.value); $('limit-value').textContent = `${config.limit} km/h`; save(); renderGauge(true); });
      $('alert').addEventListener('change', e => {
        config.alert = e.target.checked;
        if (!config.alert && cue.src.endsWith('speed-warning.mp3')) { cue.pause(); duckMusic(1); }
        save(); renderGauge(true);
      });
      $('power-tones').addEventListener('change', e => { config.powerTones = e.target.checked; save(); });
      $('demo-speed').addEventListener('input', e => { demoSpeed = Number(e.target.value); $('speed-value').textContent = `${demoSpeed} km/h`; renderGauge(true); });
      break;
    case 'lighting':
      body.innerHTML = `<p class="panel-note">In Auto, the simulated light switch changes both the photo and the day/night palette, without touching your screen brightness.</p>
        <div class="lighting-sample"><div class="lighting-sample-header">${icon('headlights')}<div><span class="eyebrow">SIMULATED CAR LIGHT SWITCH</span><b>${demoHeadlights ? 'Parking lights on' : 'Lights off'}</b></div></div>
          <div class="segmented" aria-label="Simulate vehicle lights"><button type="button" data-sim-lights="off" aria-pressed="${!demoHeadlights}">Lights off</button><button type="button" data-sim-lights="on" aria-pressed="${demoHeadlights}">Parking lights on</button></div></div>
        <section class="panel-section lighting-setup"><div class="setting-caption">On your head unit · While parked</div>
          <ol><li>Keep the factory dimmer enabled.</li><li>With the lights off, choose <b>Capture lights OFF</b>.</li><li>Turn the lights on, wait for the screen to dim, then choose <b>Capture lights ON &amp; save</b>.</li></ol>
          <small>If readings stay the same, the backlight can still dim, but the photo needs another signal.</small>
          ${config.theme !== 'auto' || config.lampMode !== 'auto' ? '<button type="button" class="action" data-action="restore-lighting">Restore Auto for colors and photo</button>' : ''}
        </section>`;
      break;
    case 'bt-audio':
      body.innerHTML = `<div class="panel-summary">${icon('bluetooth')}<span><b>Bluetooth music</b><small>HEAD UNIT APP SHORTCUT</small></span></div>
        <p class="panel-note">On Android, BT Audio opens the head unit’s Bluetooth music app. If it isn’t detected, select the app on the first tap or from Settings → App shortcuts → BT Audio.</p>
        <p class="panel-note">Pair your phone in the head unit’s Bluetooth app. The browser simulator can’t access your paired devices.</p>`;
      break;
    case 'music':
      body.innerHTML = `<p class="panel-note">Three short, original sample loops for the browser preview. The Android launcher displays your real player’s title, artist and album art.</p><div class="track-list">${tracks.map((track, i) => `<button class="track-row" type="button" data-track="${i}">${icon('music')}<span><b>${track.name}</b><small>${track.artist}</small></span></button>`).join('')}</div><div class="actions"><button class="action primary" type="button" data-action="play">Play preview</button></div>`;
      renderTrack();
      break;
    case 'phone':
    case 'radio':
      body.innerHTML = `<p class="panel-note">The browser can’t access your head unit’s ${name === 'radio' ? 'FM tuner' : 'Bluetooth dialer'}. In the Android launcher, you can assign an installed ${name === 'radio' ? 'Radio' : 'Phone'} app to this shortcut.</p><div class="actions"><button class="action" type="button" data-panel="apps">Browse shortcuts ${icon('chevron')}</button></div>`;
      break;
    case 'weather':
      body.innerHTML = `<div class="panel-summary">${icon('weather')}<span><b>27° · Partly cloudy</b><small>COLOMBO, SRI LANKA</small></span></div><p class="panel-note">Example conditions. On Android, the launcher checks Open-Meteo using your approximate location when you’re online.</p>`;
      break;
  }
  if (!panel.open) panel.showModal();
  $('panel-close').focus();
  panel.scrollTop = 0;
}
document.addEventListener('click', e => {
  const button = e.target.closest('[data-panel],[data-theme],[data-lamp-mode],[data-sim-lights],[data-gauge],[data-track],[data-action]');
  if (!button) return;
  const data = button.dataset;
  if (data.panel) show(data.panel);
  if (data.theme) { config.theme = data.theme; save(); renderAppearance(); }
  if (data.lampMode) { config.lampMode = data.lampMode; save(); renderAppearance(); }
  if (data.simLights) { simulateHeadlights(data.simLights === 'on'); panel.close(); }
  if (data.gauge) { config.gaugeStyle = data.gauge; save(); renderAppearance(); }
  if (data.track !== undefined) chooseTrack(Number(data.track), true);
  if (data.action === 'restore-lighting') {
    config.theme = config.lampMode = 'auto';
    save(); renderAppearance(); show('lighting');
  }
  if (data.action === 'startup') greeting();
  if (data.action === 'goodbye') greeting(true);
  if (data.action === 'warning') playCue('speed-warning.mp3', .82);
  if (data.action === 'play') toggleMusic();
  if (data.action === 'reset') {
    trip = 0; drive = 0; lastTick = Date.now(); renderTrip(); panel.close(); toast('Trip distance and drive time reset.');
  }
});
const catalog = [
  ['phone', 'phone', 'Bluetooth phone', 'Hands-free calling', 'sage'],
  ['bt-audio', 'bluetooth', 'BT Audio', 'Bluetooth music', 'blue'],
  ['', 'navigation', 'Google Maps', 'Map app', 'sage', 'https://www.google.com/maps/'],
  ['music', 'music', 'Music', 'Media player', 'plum'],
  ['radio', 'radio', 'Radio', 'FM tuner', 'copper'],
  ['settings', 'settings', 'Settings', 'Drive console', 'copper'],
  ['', 'compass', 'Waze', 'Map app', 'blue', 'https://www.waze.com/live-map/'],
  ['weather', 'weather', 'Weather', 'Current conditions', 'plum'],
];
function renderApps() {
  const query = $('apps-search').value.trim().toLowerCase();
  const matches = catalog.filter(([, , label, detail]) => `${label} ${detail}`.toLowerCase().includes(query));
  $('apps-grid').innerHTML = matches.map(([target, symbol, label, detail, hue, url]) => {
    const contents = `<span class="apps-entry-icon ${hue}">${icon(symbol)}</span><b>${label}</b><small>${detail}</small>`;
    return url ? `<a class="apps-entry" href="${url}" target="_blank" rel="noopener noreferrer">${contents}</a>`
      : `<button class="apps-entry" type="button" data-panel="${target}">${contents}</button>`;
  }).join('');
  $('apps-grid').scrollTop = 0;
  $('apps-count').textContent = `${matches.length} ${matches.length === 1 ? 'APP' : 'APPS'} / A–Z`;
  $('apps-empty').hidden = matches.length > 0;
}
function syncApps(focus = false) {
  const open = location.hash === '#apps';
  if (open === !$('apps-page').hidden) return;
  if (panel.open) panel.close();
  $('apps-page').hidden = !open;
  consoleView.classList.toggle('showing-apps', open);
  syncDashboardInput();
  if (open) {
    $('apps-search').value = '';
    renderApps();
    window.scrollTo(0,0);
    if (focus) $('apps-heading').focus({ preventScroll: true });
  } else if (focus) document.querySelector('.dock-button[data-panel="apps"]').focus({ preventScroll: true });
}
function openApps() {
  if (location.hash !== '#apps') history.pushState({ vezelFromDashboard: true }, '', '#apps');
  syncApps(true);
}
function closeApps() {
  if (history.state?.vezelFromDashboard) history.back();
  else {
    history.replaceState(null, '', location.pathname + location.search);
    syncApps(true);
  }
}
$('apps-search').addEventListener('input', renderApps);
$('apps-back').addEventListener('click', closeApps);
window.addEventListener('hashchange', () => syncApps(true));
window.addEventListener('popstate', () => syncApps(true));
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !$('apps-page').hidden && !panel.open && $('welcome').hidden) closeApps();
});
syncApps();
$('panel-close').addEventListener('click', () => panel.close());
panel.addEventListener('click', e => { if (e.target === panel) panel.close(); });
renderAppearance(); renderGauge(); renderTrip(); renderTrack();

checkApk();
