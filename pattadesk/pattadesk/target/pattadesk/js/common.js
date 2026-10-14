'use strict';
/* Shared helpers for the login page and both portals. */

const BASE = window.BASE || '';   // '' on the login page, '../' inside /user and /admin
let CSRF = '';

// ---------- small utilities ----------
function esc(value) {
  return String(value == null ? '' : value).replace(/[&<>"']/g, ch => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}
const dash = v => (v == null || v === '') ? '&mdash;' : esc(v);
const money = n => (n == null) ? '&mdash;'
  : new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);

const LABELS = {
  status: { submitted: 'Submitted', assigned: 'Assigned to Admin', in_progress: 'In Progress', resolved: 'Resolved', rejected: 'Rejected' },
  land: { clear: 'Clear', mortgaged: 'Mortgaged', disputed: 'Disputed' },
  category: { encroachment: 'Encroachment', boundary_dispute: 'Boundary Dispute', incorrect_record: 'Incorrect Record', mutation_delay: 'Mutation Delay', other: 'Other' },
  priority: { low: 'Low', medium: 'Medium', high: 'High' },
  transfer: { initial: 'Initial allotment', sale: 'Sale', inheritance: 'Inheritance', gift: 'Gift', partition: 'Partition', court_order: 'Court order' }
};
function label(group, key) { return (LABELS[group] && LABELS[group][key]) || key || ''; }
function badge(group, key) { return `<span class="badge b-${esc(key)}">${esc(label(group, key))}</span>`; }

function options(group, selected) {
  return Object.keys(LABELS[group]).map(k =>
    `<option value="${esc(k)}"${k === selected ? ' selected' : ''}>${esc(label(group, k))}</option>`).join('');
}

// ---------- server calls ----------
async function request(method, url, data) {
  const opts = { method, credentials: 'same-origin', headers: { 'Accept': 'application/json' } };
  if (method === 'POST') {
    opts.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
    opts.headers['X-CSRF-Token'] = CSRF;
    opts.body = new URLSearchParams(data || {}).toString();
  }
  let res;
  try {
    res = await fetch(BASE + url, opts);
  } catch (e) {
    throw new Error('Cannot reach the server. Is Tomcat running?');
  }
  let body = null;
  try { body = await res.json(); } catch (e) { /* no JSON body */ }
  if (res.status === 401) {
    location.href = BASE + 'index.html';
    throw new Error('Your session has ended. Please log in again.');
  }
  if (!res.ok) {
    throw new Error((body && body.error) || ('Request failed (' + res.status + ')'));
  }
  return body;
}
const api = {
  get: (url, params) => request('GET', url + toQuery(params)),
  post: (url, data) => request('POST', url, data)
};
function toQuery(params) {
  if (!params) return '';
  const q = new URLSearchParams();
  Object.keys(params).forEach(k => { if (params[k] != null && params[k] !== '') q.set(k, params[k]); });
  const s = q.toString();
  return s ? '?' + s : '';
}

// ---------- feedback ----------
function toast(message, type) {
  const box = document.getElementById('toasts');
  if (!box) return;
  const el = document.createElement('div');
  el.className = 'toast' + (type === 'err' ? ' err' : '');
  el.textContent = message;
  box.appendChild(el);
  setTimeout(() => el.remove(), 4500);
}

function confirmBox(message, okText) {
  return new Promise(resolve => {
    const dlg = document.createElement('dialog');
    dlg.innerHTML = `<p>${esc(message)}</p><div class="actions">
      <button class="btn" data-v="0">Cancel</button>
      <button class="btn btn-primary" data-v="1">${esc(okText || 'Confirm')}</button></div>`;
    dlg.addEventListener('click', e => {
      const v = e.target && e.target.dataset && e.target.dataset.v;
      if (v === '0' || v === '1') { dlg.close(); dlg.remove(); resolve(v === '1'); }
    });
    dlg.addEventListener('cancel', () => { dlg.remove(); resolve(false); });
    document.body.appendChild(dlg);
    dlg.showModal();
  });
}

/** Runs an async action on a button: disables it while working, shows errors as toasts. */
async function withButton(btn, work) {
  if (btn.disabled) return;
  btn.disabled = true;
  try { await work(); } catch (e) { toast(e.message, 'err'); } finally { btn.disabled = false; }
}

// ---------- shared view pieces ----------
const kv = (name, valueHtml) => `<div class="kv"><span>${esc(name)}</span><b>${valueHtml}</b></div>`;
const errorBox = msg => `<div class="alert err">${esc(msg)}</div>`;

function table(headers, rowsHtml, emptyText) {
  if (!rowsHtml.length) return `<div class="table-wrap"><div class="empty">${esc(emptyText)}</div></div>`;
  return `<div class="table-wrap"><table><thead><tr>${headers.map(h => `<th>${esc(h)}</th>`).join('')}</tr></thead>
    <tbody>${rowsHtml.join('')}</tbody></table></div>`;
}

function historyTable(history) {
  return table(['Owner', 'From', 'To', 'Transfer', 'Deed no.', 'Amount', 'Remarks'],
    history.map(h => `<tr><td>${esc(h.ownerName)}</td><td>${dash(h.startDate)}</td>
      <td>${h.endDate ? esc(h.endDate) : '<span class="badge b-resolved">Current</span>'}</td>
      <td>${esc(label('transfer', h.transferType))}</td><td>${dash(h.deedNo)}</td>
      <td>${money(h.consideration)}</td><td>${dash(h.remarks)}</td></tr>`), 'No ownership history recorded.');
}

function priceTable(prices) {
  return table(['Year', 'Market value', 'Government value'],
    prices.map(p => `<tr><td>${esc(p.recYear)}</td><td>${money(p.marketValue)}</td><td>${money(p.govtValue)}</td></tr>`),
    'No valuations recorded.');
}

// ---------- portal shell (sidebar + top bar) ----------
/**
 * Loads /api/me, checks the role, draws the sidebar and calls router() on every hash change.
 * portal = 'citizen' | 'admin'
 */
async function startPortal(portal, navItems, router) {
  let me;
  try {
    me = await api.get('api/me');
  } catch (e) {
    document.getElementById('root').innerHTML = errorBox(e.message);
    return;
  }
  CSRF = me.csrf;
  if (me.role !== portal) { location.href = BASE + 'index.html'; return; }

  const title = portal === 'admin' ? 'Admin Portal' : 'Citizen Portal';
  document.getElementById('root').innerHTML = `
    <div class="layout">
      <aside class="sidebar" id="sidebar">
        <div class="brand"><div class="brand-mark">P</div>
          <div><strong>PattaDesk</strong><small>${title}</small></div></div>
        <nav id="nav">${navItems.map(n => `<a href="#/${n.route}" data-route="${n.route}">${esc(n.label)}</a>`).join('')}</nav>
        <button class="nav-logout" id="logoutBtn">Logout</button>
      </aside>
      <div class="content">
        <header class="topbar">
          <button class="menu-btn" id="menuBtn" aria-label="Open menu">&#9776;</button>
          <div class="who"><span>${esc(me.name)}</span><span class="pill">${esc(me.role)}</span></div>
        </header>
        <main class="view" id="view"></main>
      </div>
    </div>
    <div id="toasts"></div>`;

  document.getElementById('menuBtn').addEventListener('click', () => document.getElementById('sidebar').classList.toggle('open'));
  document.getElementById('nav').addEventListener('click', () => document.getElementById('sidebar').classList.remove('open'));
  document.getElementById('logoutBtn').addEventListener('click', async () => {
    try { await api.post('api/logout', {}); } catch (e) { /* session may already be gone */ }
    location.href = BASE + 'index.html';
  });
  window.addEventListener('hashchange', router);
  router();
  return me;
}

function setActive(route) {
  document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('active', a.dataset.route === route));
}
function view() { return document.getElementById('view'); }
function currentRoute(defaultPage) {
  const raw = location.hash.replace(/^#\/?/, '') || defaultPage;
  const i = raw.indexOf('/');
  return i < 0 ? { page: raw, arg: '' } : { page: raw.slice(0, i), arg: raw.slice(i + 1) };
}
function debounce(fn, ms) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}
