import { attachShowroom } from './vehicle.js';

const $ = (id) => document.getElementById(id);
const consoleView = $('console');
const lightShowroom = attachShowroom();
const panel = $('panel');
const body = $('panel-content');
const namespace = 'http://www.w3.org/2000/svg';
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
const config = { theme: 'day', lamps: true, limit: 60, alert: true, powerTones: true, track: 0, provider: 'maps' };
try {
  const saved = JSON.parse(localStorage.getItem(storageKey));
  if (saved && typeof saved === 'object') {
    if (['auto', 'day', 'night'].includes(saved.theme)) config.theme = saved.theme;
    if (typeof saved.lamps === 'boolean') config.lamps = saved.lamps;
    if (typeof saved.alert === 'boolean') config.alert = saved.alert;
    if (typeof saved.powerTones === 'boolean') config.powerTones = saved.powerTones;
    if (Number.isInteger(saved.limit) && saved.limit >= 20 && saved.limit <= 140) config.limit = saved.limit;
    if (Number.isInteger(saved.track) && tracks[saved.track]) config.track = saved.track;
    if (['maps', 'waze'].includes(saved.provider)) config.provider = saved.provider;
  }
} catch { /* The preview also works with storage disabled. */ }
const save = () => { try { localStorage.setItem(storageKey, JSON.stringify(config)); } catch {} };
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

const start = 138;
const sweep = 264;
const polar = (r, degrees) => [120 + Math.cos(degrees * Math.PI / 180) * r, 96 + Math.sin(degrees * Math.PI / 180) * r];
const arc = (end) => `M${polar(91, start).join(' ')} A91 91 0 ${end - start > 180 ? 1 : 0} 1 ${polar(91, end).join(' ')}`;
$('dial-track').setAttribute('d', arc(start + sweep));
const ticks = Array.from({ length: 41 }, (_, i) => {
  const line = document.createElementNS(namespace, 'line');
  const major = i % 5 === 0;
  const angle = start + i / 40 * sweep;
  const [x1, y1] = polar(major ? 76 : 80, angle);
  const [x2, y2] = polar(85, angle);
  for (const [key, val] of Object.entries({ x1, y1, x2, y2, 'stroke-width': major ? 1.4 : 0.85 })) line.setAttribute(key, val);
  line.classList.toggle('major', major);
  $('dial-ticks').append(line);
  return line;
});
function renderGauge(allowVoice = false) {
  const progress = Math.min(demoSpeed / 160, 1);
  const angle = start + sweep * progress;
  const [x, y] = polar(91, angle);
  $('dial-arc').setAttribute('d', progress ? arc(angle) : '');
  $('dial-point').setAttribute('cx', x);
  $('dial-point').setAttribute('cy', y);
  $('dial-point').style.visibility = progress ? 'visible' : 'hidden';
  ticks.forEach((line, i) => line.classList.toggle('reached', i / 40 <= progress && progress > 0));
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
  const isNight = config.theme === 'night' || (config.theme === 'auto' && config.lamps);
  consoleView.classList.toggle('night-mode', isNight);
  consoleView.classList.toggle('lights-on', config.lamps);
  lightShowroom(config.lamps, isNight);
  $('night').setAttribute('aria-pressed', String(isNight));
  $('night').setAttribute('title', isNight ? 'Switch to day display' : 'Switch to night display');
  $('night').querySelector('use').setAttribute('href', `/icons.svg#${isNight ? 'sun' : 'moon'}`);
  $('lamps').setAttribute('aria-pressed', String(config.lamps));
  $('light-label').textContent = config.lamps ? 'LIGHTS + FOG ON' : 'LIGHTS + FOG OFF';
  renderThemeButtons();
}
function renderThemeButtons() {
  body.querySelectorAll('[data-theme]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.theme === config.theme)));
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
  config.theme = consoleView.classList.contains('night-mode') ? 'day' : 'night';
  save(); renderAppearance();
});
$('lamps').addEventListener('click', () => { config.lamps = !config.lamps; save(); renderAppearance(); });

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
  navigation: ['ROUTE PREVIEW', 'Navigation'],
  music: ['VEZEL SESSIONS', 'Now playing'],
  phone: ['ANDROID SHORTCUT', 'Phone'],
  radio: ['ANDROID SHORTCUT', 'Radio'],
  weather: ['SIMULATED WEATHER', 'Colombo'],
};
const providerSegment = () => `<div class="segmented" aria-label="Preferred navigation app">${[['maps', 'Google Maps'], ['waze', 'Waze']].map(([key, label]) => `<button type="button" data-provider="${key}" aria-pressed="${config.provider === key}">${label}</button>`).join('')}</div>`;
function show(name) {
  if (name === 'apps') { openApps(); return; }
  if (!(name in panels)) return;
  $('panel-kicker').textContent = panels[name][0];
  $('panel-title').textContent = panels[name][1];
  switch (name) {
    case 'settings':
      body.innerHTML = `<p class="panel-note">Try the console here with sample data. The Android app uses your head unit’s GPS, notifications and installed apps.</p>
        <section class="panel-section"><div class="setting-caption">Display mode</div><div class="segmented" aria-label="Display mode">${['auto', 'day', 'night'].map(mode => `<button type="button" data-theme="${mode}" aria-pressed="${config.theme === mode}">${mode.charAt(0).toUpperCase() + mode.slice(1)}</button>`).join('')}</div></section>
        <section class="panel-section"><label for="limit">Speed alert <output id="limit-value">${config.limit} km/h</output></label><input type="range" id="limit" min="20" max="140" value="${config.limit}"><label class="check-row" for="alert">Speak when the limit is reached<input type="checkbox" id="alert" ${config.alert ? 'checked' : ''}></label></section>
        <section class="panel-section"><label for="demo-speed">Simulated GPS speed <output id="speed-value">${demoSpeed} km/h</output></label><input type="range" id="demo-speed" min="0" max="160" value="${demoSpeed}"></section>
        <div class="actions"><button type="button" class="action" data-action="warning">Preview warning voice</button><button type="button" class="action" data-action="reset">Reset trip</button><a id="apk" class="action primary" href="${apkUrl}" data-apk download hidden>${icon('download')} Android APK</a></div><section class="panel-section"><div class="setting-caption">Power cues</div><label class="check-row" for="power-tones">Welcome and Good Bye tones<input id="power-tones" type="checkbox" ${config.powerTones ? 'checked' : ''}></label><div class="actions"><button type="button" class="action" data-action="startup">Preview Welcome</button><button type="button" class="action" data-action="goodbye">Preview Good Bye</button></div><p class="panel-note">These previews don’t change Android power. Automatic key-off depends on the head unit.</p></section><p id="apk-missing" class="panel-note" hidden>The APK is not available yet. Build it from the Android project to enable the download.</p>`;
      updateApkLinks();
      checkApk();
      $('limit').addEventListener('input', e => { config.limit = Number(e.target.value); $('limit-value').textContent = `${config.limit} km/h`; save(); renderGauge(true); });
      $('alert').addEventListener('change', e => {
        config.alert = e.target.checked;
        if (!config.alert && cue.src.endsWith('speed-warning.mp3')) { cue.pause(); duckMusic(1); }
        save(); renderGauge(true);
      });
      $('power-tones').addEventListener('change', e => { config.powerTones = e.target.checked; save(); });
      $('demo-speed').addEventListener('input', e => { demoSpeed = Number(e.target.value); $('speed-value').textContent = `${demoSpeed} km/h`; renderGauge(true); });
      break;
    case 'navigation':
      body.innerHTML = `<div class="route-summary"><span class="turn">${icon('turn_right')}</span><span><b>400 m · Turn right onto Galle Rd</b><small>ARRIVAL 9:14 AM / 6.2 KM REMAINING</small></span></div>
        <p class="panel-note">This is a sample route. On Android, allow notification access to see directions from Google Maps or Waze when their notifications include turn details.</p>
        <section class="panel-section"><div class="setting-caption">Preferred navigation app</div>${providerSegment()}</section>
        <div class="actions"><a id="map-link" class="action primary" target="_blank" rel="noreferrer">Open map ${icon('chevron')}</a></div>`;
      renderProvider();
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
      body.innerHTML = `<div class="route-summary">${icon('weather')}<span><b>27° · Partly cloudy</b><small>COLOMBO, SRI LANKA</small></span></div><p class="panel-note">Example conditions. On Android, the launcher checks Open-Meteo using your approximate location when you’re online.</p>`;
      break;
  }
  if (!panel.open) panel.showModal();
  $('panel-close').focus();
  panel.scrollTop = 0;
}
function renderProvider() {
  const link = $('map-link');
  if (!link) return;
  link.href = config.provider === 'waze' ? 'https://www.waze.com/live-map/' : 'https://www.google.com/maps/search/?api=1&query=Galle+Road%2C+Colombo';
  link.innerHTML = `Open ${config.provider === 'waze' ? 'Waze' : 'Google Maps'} ${icon('chevron')}`;
  body.querySelectorAll('[data-provider]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.provider === config.provider)));
}
document.addEventListener('click', e => {
  const button = e.target.closest('[data-panel],[data-theme],[data-track],[data-provider],[data-action]');
  if (!button) return;
  const data = button.dataset;
  if (data.appProvider) { config.provider = data.appProvider; save(); }
  if (data.panel) show(data.panel);
  if (data.theme) { config.theme = data.theme; save(); renderAppearance(); }
  if (data.track !== undefined) chooseTrack(Number(data.track), true);
  if (data.provider) { config.provider = data.provider; save(); renderProvider(); }
  if (data.action === 'startup') greeting();
  if (data.action === 'goodbye') greeting(true);
  if (data.action === 'warning') playCue('speed-warning.mp3', .82);
  if (data.action === 'play') toggleMusic();
  if (data.action === 'reset') {
    trip = 0; drive = 0; lastTick = Date.now(); renderTrip(); panel.close(); toast('Trip distance and drive time reset.');
  }
});
const catalog = [
  ['phone', 'phone', 'Bluetooth phone', 'Hands-free calling'],
  ['navigation', 'navigation', 'Google Maps', 'Navigation', 'maps'],
  ['music', 'music', 'Music', 'Media player'],
  ['radio', 'radio', 'Radio', 'FM tuner'],
  ['settings', 'settings', 'Settings', 'Drive console'],
  ['navigation', 'compass', 'Waze', 'Navigation', 'waze'],
  ['weather', 'weather', 'Weather', 'Current conditions'],
];
function renderApps() {
  const query = $('apps-search').value.trim().toLowerCase();
  const matches = catalog.filter(([, , label, detail]) => `${label} ${detail}`.toLowerCase().includes(query));
  $('apps-grid').innerHTML = matches.map(([target, symbol, label, detail, provider]) =>
    `<button class="apps-entry" type="button" data-panel="${target}" ${provider ? `data-app-provider="${provider}"` : ''}><span class="apps-entry-icon">${icon(symbol)}</span><b>${label}</b><small>${detail}</small></button>`).join('');
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
