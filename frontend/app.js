'use strict';

const GW = `http://${location.hostname}:18080`;
const WS = `ws://${location.hostname}:18080/ws`;

const state = {
  token: null, refreshToken: null, userId: null, username: null,
  conversations: [], activeConv: null, messages: {}, lastSeq: {}, presence: {},
  mode: 'login',
};

const $ = (id) => document.getElementById(id);

// ---------- REST ----------
async function api(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
  const res = await fetch(GW + path, { ...opts, headers });
  if (res.status === 401 && state.refreshToken && !opts._retried) {
    await refreshTokens();
    return api(path, { ...opts, _retried: true });
  }
  if (!res.ok) {
    let msg = res.statusText;
    try { msg = (await res.json()).message || msg; } catch (_) { /* ignore */ }
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

async function refreshTokens() {
  const res = await fetch(GW + '/auth/refresh', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: state.refreshToken }),
  });
  if (!res.ok) throw new Error('session expired');
  const data = await res.json();
  state.token = data.accessToken;
  state.refreshToken = data.refreshToken;
}

// Decode the JWT exp client-side so a WS reconnect never presents an expired token.
function tokenExpiringSoon() {
  if (!state.token) return true;
  try {
    const payload = JSON.parse(atob(state.token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return (payload.exp * 1000 - Date.now()) < 60000;
  } catch (_) {
    return true;
  }
}

async function ensureFreshToken() {
  if (tokenExpiringSoon() && state.refreshToken) {
    try { await refreshTokens(); } catch (_) { /* CONNECT will surface the failure */ }
  }
}

// ---------- STOMP over WebSocket ----------
class StompClient {
  constructor(onFrame, onState) {
    this.onFrame = onFrame;
    this.onState = onState;
    this.ws = null;
    this.reconnectDelay = 1000;
    this.subscriptions = [];
    this.closedByUser = false;
  }
  async connect() {
    await ensureFreshToken();
    this.ws = new WebSocket(`${WS}?access_token=${encodeURIComponent(state.token)}`);
    this.ws.onopen = () => {
      this._send('CONNECT', { 'accept-version': '1.2', host: location.hostname, 'heart-beat': '10000,10000' });
      this._heartbeat = setInterval(() => {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) this.ws.send('\n');
      }, 10000);
    };
    this.ws.onmessage = (ev) => this._parse(String(ev.data));
    this.ws.onclose = () => {
      clearInterval(this._heartbeat);
      this.onState(false);
      if (!this.closedByUser) setTimeout(() => this.connect(), this.reconnectDelay);
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, 15000);
    };
    this.ws.onerror = () => this.ws.close();
  }
  _send(cmd, headers, body = '') {
    let frame = cmd + '\n';
    for (const [k, v] of Object.entries(headers)) frame += `${k}:${v}\n`;
    frame += '\n' + body + '\0';
    this.ws.send(frame);
  }
  _parse(data) {
    for (const frame of data.split('\0')) {
      if (!frame.trim()) continue;
      const idx = frame.indexOf('\n\n');
      const head = idx >= 0 ? frame.slice(0, idx) : frame;
      const body = idx >= 0 ? frame.slice(idx + 2) : '';
      const lines = head.split('\n');
      const cmd = lines[0];
      const headers = {};
      for (const line of lines.slice(1)) {
        const i = line.indexOf(':');
        if (i > 0) headers[line.slice(0, i)] = line.slice(i + 1);
      }
      if (cmd === 'CONNECTED') {
        this.reconnectDelay = 1000;
        this.onState(true);
        for (const sub of this.subscriptions) this._send('SUBSCRIBE', sub);
        this.onFrame('connected', headers, body);
      } else if (cmd === 'MESSAGE') {
        this.onFrame('message', headers, body);
      } else if (cmd === 'ERROR') {
        this.onFrame('error', headers, body);
      }
    }
  }
  subscribe(destination) {
    const sub = { id: 'sub-' + this.subscriptions.length, destination };
    this.subscriptions.push(sub);
    if (this.ws && this.ws.readyState === WebSocket.OPEN) this._send('SUBSCRIBE', sub);
  }
  send(destination, payload) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this._send('SEND', { destination, 'content-type': 'application/json' }, JSON.stringify(payload));
    }
  }
  close() { this.closedByUser = true; if (this.ws) this.ws.close(); }
}

let stomp = null;

// ---------- UI rendering ----------
function renderConversations() {
  const ul = $('conversations');
  ul.innerHTML = '';
  for (const c of state.conversations) {
    const li = document.createElement('li');
    li.dataset.id = c.id;
    if (c.id === state.activeConv) li.classList.add('active');
    const label = c.type === 'GROUP' ? (c.name || 'Group') : 'Direct ' + c.id.slice(0, 8);
    li.innerHTML = `${label} <span class="badge">${c.type} · ${c.members.length} members</span>`;
    li.onclick = () => openConversation(c.id);
    ul.appendChild(li);
  }
}

function renderMessages() {
  const box = $('messages');
  box.innerHTML = '';
  const list = state.messages[state.activeConv] || [];
  for (const m of list) {
    const div = document.createElement('div');
    div.className = 'msg' + (m.senderId === state.userId ? ' mine' : '');
    div.dataset.messageId = m.messageId;
    const status = m.senderId === state.userId ? `<span class="status">${m.status || '✓'}</span>` : '';
    div.innerHTML = `${escapeHtml(m.body)}<div class="meta">seq ${m.seq}${status}</div>`;
    box.appendChild(div);
  }
  box.scrollTop = box.scrollHeight;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function setConnState(online) {
  $('conn-state').className = 'dot ' + (online ? 'online' : 'offline');
  $('conn-label').textContent = online ? 'connected' : 'reconnecting…';
}

function updatePeerPresence() {
  const conv = state.conversations.find((c) => c.id === state.activeConv);
  const el = $('peer-presence');
  if (!conv) { el.textContent = ''; return; }
  const others = conv.members.filter((m) => m.userId !== state.userId);
  const online = others.some((m) => state.presence[m.userId]);
  el.textContent = online ? '● online' : '○ offline';
  el.className = 'presence' + (online ? ' online' : '');
}

// ---------- actions ----------
async function loadConversations() {
  state.conversations = await api('/conversations');
  renderConversations();
}

async function openConversation(id) {
  state.activeConv = id;
  const conv = state.conversations.find((c) => c.id === id);
  $('conv-title').textContent = conv ? (conv.type === 'GROUP' ? conv.name : 'Direct conversation') : id;
  $('message-input').disabled = false;
  $('send-btn').disabled = false;
  renderConversations();
  const msgs = await api(`/history/conversations/${id}/messages?afterSeq=0&limit=200`);
  state.messages[id] = msgs.map((m) => ({ ...m, status: m.senderId === state.userId ? '✓✓' : '' }));
  state.lastSeq[id] = msgs.reduce((max, m) => Math.max(max, m.seq), 0);
  renderMessages();
  await loadPresence(conv);
  if (state.lastSeq[id] > 0) stomp.send('/app/chat.receipt', { conversationId: id, type: 'READ', upToSeq: state.lastSeq[id] });
}

async function loadPresence(conv) {
  if (!conv) return;
  const ids = conv.members.map((m) => m.userId).join(',');
  const list = await api('/presence?ids=' + ids);
  for (const p of list) state.presence[p.userId] = p.online;
  updatePeerPresence();
}

async function gapSync() {
  for (const id of Object.keys(state.messages)) {
    const after = state.lastSeq[id] || 0;
    const msgs = await api(`/history/conversations/${id}/messages?afterSeq=${after}&limit=200`);
    if (msgs.length) {
      state.messages[id].push(...msgs.map((m) => ({ ...m, status: m.senderId === state.userId ? '✓✓' : '' })));
      state.lastSeq[id] = msgs.reduce((max, m) => Math.max(max, m.seq), after);
      if (id === state.activeConv) renderMessages();
    }
  }
}

function handleFrame(kind, headers, body) {
  if (kind === 'connected') {
    gapSync().catch(() => {});
    api('/presence/' + state.userId).then((p) => { $('instance-id').textContent = p.instanceId ? 'via ' + p.instanceId.slice(0, 12) : ''; }).catch(() => {});
    return;
  }
  if (kind === 'error') {
    console.warn('STOMP error', body);
    // Usually an expired/rejected token on (re)connect: close so onclose reconnects
    // with a freshly refreshed token instead of retrying the stale one.
    if (stomp && stomp.ws) { stomp.reconnectDelay = 1000; try { stomp.ws.close(); } catch (_) {} }
    return;
  }
  const dest = headers.destination || '';
  const channel = (dest.split('/queue/')[1] || '').split('-user')[0];
  let payload;
  try { payload = JSON.parse(body); } catch (_) { return; }

  if (channel === 'messages') {
    const list = state.messages[payload.conversationId] || (state.messages[payload.conversationId] = []);
    if (!list.some((m) => m.messageId === payload.messageId)) {
      list.push({ ...payload, status: payload.senderId === state.userId ? '✓✓' : '' });
      state.lastSeq[payload.conversationId] = Math.max(state.lastSeq[payload.conversationId] || 0, payload.seq);
      if (payload.conversationId === state.activeConv) {
        renderMessages();
        stomp.send('/app/chat.receipt', { conversationId: payload.conversationId, type: 'READ', upToSeq: payload.seq });
      }
    }
  } else if (channel === 'acks') {
    const list = state.messages[state.activeConv] || [];
    const m = list.find((x) => x.messageId === payload.messageId);
    if (m) { m.status = '✓'; renderMessages(); }
  } else if (channel === 'typing') {
    if (payload.conversationId === state.activeConv && payload.userId !== state.userId) {
      $('typing').textContent = 'typing…';
      clearTimeout(window._typingTimer);
      window._typingTimer = setTimeout(() => { $('typing').textContent = ''; }, 2000);
    }
  } else if (channel === 'receipts') {
    const list = state.messages[payload.conversationId] || [];
    for (const m of list) {
      if (m.senderId === state.userId && m.seq <= payload.upToSeq) {
        m.status = payload.type === 'READ' ? '✓✓ read' : '✓✓';
      }
    }
    if (payload.conversationId === state.activeConv) renderMessages();
  } else if (channel === 'presence') {
    state.presence[payload.userId] = payload.online;
    updatePeerPresence();
  } else if (channel === 'conversations') {
    loadConversations().catch(() => {});
  } else if (channel === 'errors') {
    $('typing').textContent = payload.message || 'error';
    setTimeout(() => { $('typing').textContent = ''; }, 2500);
  }
}

function enterApp() {
  $('auth').classList.add('hidden');
  $('app').classList.remove('hidden');
  $('me-name').textContent = state.username;
  $('me-id').textContent = state.userId;
  stomp = new StompClient(handleFrame, setConnState);
  for (const q of ['messages', 'acks', 'errors', 'typing', 'receipts', 'presence', 'conversations']) {
    stomp.subscribe('/user/queue/' + q);
  }
  stomp.connect();
  loadConversations().catch(() => {});
}

// ---------- wiring ----------
function setMode(mode) {
  state.mode = mode;
  $('tab-login').classList.toggle('active', mode === 'login');
  $('tab-register').classList.toggle('active', mode === 'register');
  $('auth-submit').textContent = mode === 'login' ? 'Login' : 'Register';
  $('email').style.display = mode === 'register' ? 'block' : 'none';
}

$('tab-login').onclick = () => setMode('login');
$('tab-register').onclick = () => setMode('register');

$('auth-form').onsubmit = async (e) => {
  e.preventDefault();
  $('auth-error').textContent = '';
  const username = $('username').value.trim();
  const password = $('password').value;
  try {
    const path = state.mode === 'login' ? '/auth/login' : '/auth/register';
    const body = state.mode === 'login' ? { username, password } : { username, password, email: $('email').value.trim() };
    const res = await fetch(GW + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.message || res.statusText); }
    const data = await res.json();
    state.token = data.accessToken; state.refreshToken = data.refreshToken;
    state.userId = data.userId; state.username = data.username;
    enterApp();
  } catch (err) {
    $('auth-error').textContent = err.message;
  }
};

$('new-direct').onclick = async () => {
  $('auth-error').textContent = '';
  try {
    const conv = await api('/conversations/direct', { method: 'POST', body: JSON.stringify({ peerId: $('peer-id').value.trim() }) });
    await loadConversations();
    await openConversation(conv.id);
  } catch (err) { alert(err.message); }
};

$('new-group').onclick = async () => {
  try {
    const memberIds = $('group-members').value.split(',').map((s) => s.trim()).filter(Boolean);
    const conv = await api('/conversations/group', { method: 'POST', body: JSON.stringify({ name: $('group-name').value.trim(), memberIds }) });
    await loadConversations();
    await openConversation(conv.id);
  } catch (err) { alert(err.message); }
};

$('compose').onsubmit = (e) => {
  e.preventDefault();
  const input = $('message-input');
  const body = input.value.trim();
  if (!body || !state.activeConv) return;
  stomp.send('/app/chat.send', { conversationId: state.activeConv, messageId: crypto.randomUUID(), body });
  input.value = '';
};

let typingThrottle = 0;
$('message-input').oninput = () => {
  const now = Date.now();
  if (state.activeConv && now - typingThrottle > 1500) {
    typingThrottle = now;
    stomp.send('/app/chat.typing', { conversationId: state.activeConv, typing: true });
  }
};

$('my-id').onclick = () => {
  if (state.userId && navigator.clipboard) {
    navigator.clipboard.writeText(state.userId).then(() => {
      $('my-id').title = 'Copied!';
      setTimeout(() => { $('my-id').title = 'Click to copy your user ID'; }, 1500);
    }).catch(() => {});
  }
};

$('logout').onclick = () => {
  if (stomp) stomp.close();
  state.token = state.refreshToken = state.userId = null;
  state.conversations = []; state.messages = {}; state.activeConv = null;
  $('app').classList.add('hidden');
  $('auth').classList.remove('hidden');
};

setMode('login');
