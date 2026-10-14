'use strict';
/* Citizen portal: dashboard, profile, land records, grievances. */

const NAV = [
  { route: 'dashboard', label: 'Dashboard' },
  { route: 'profile', label: 'My Profile' },
  { route: 'parcels', label: 'My Land Records' },
  { route: 'grievances', label: 'Grievances' }
];
const ACTIVE = { dashboard: 'dashboard', profile: 'profile', parcels: 'parcels', parcel: 'parcels',
                 grievances: 'grievances', grievance: 'grievances', submit: 'grievances' };

async function router() {
  const { page, arg } = currentRoute('dashboard');
  setActive(ACTIVE[page]);
  view().innerHTML = '<p class="muted">Loading...</p>';
  try {
    switch (page) {
      case 'dashboard': await pageDashboard(); break;
      case 'profile': await pageProfile(); break;
      case 'parcels': await pageParcels(); break;
      case 'parcel': await pageParcelDetail(arg); break;
      case 'grievances': await pageGrievances(); break;
      case 'grievance': await pageGrievanceDetail(arg); break;
      case 'submit': await pageSubmit(); break;
      default: location.hash = '#/dashboard';
    }
  } catch (e) {
    view().innerHTML = errorBox(e.message);
  }
}

// ---------- reusable tables ----------
function parcelTable(list) {
  return table(['Survey no.', 'District', 'Village', 'Area', 'Land type', 'Status', ''],
    list.map(p => `<tr><td>${esc(p.surveyNo)}</td><td>${esc(p.district)}</td><td>${esc(p.village)}</td>
      <td>${esc(p.areaAcres)} acres</td><td>${esc(p.landType)}</td><td>${badge('land', p.status)}</td>
      <td><a class="btn btn-sm" href="#/parcel/${p.parcelId}">View</a></td></tr>`),
    'No land records are registered in your name.');
}

function grievanceTable(list) {
  return table(['Ticket', 'Survey no.', 'Category', 'Subject', 'Priority', 'Submitted', 'Status', ''],
    list.map(g => `<tr><td>${esc(g.ticketId)}</td><td>${esc(g.surveyNo)}</td><td>${esc(label('category', g.category))}</td>
      <td>${esc(g.subject)}</td><td>${badge('priority', g.priority)}</td><td>${esc(g.createdAt)}</td>
      <td>${badge('status', g.status)}</td><td><a class="btn btn-sm" href="#/grievance/${g.grievanceId}">View</a></td></tr>`),
    'You have not submitted any grievances yet.');
}

// ---------- pages ----------
async function pageDashboard() {
  const d = await api.get('api/citizen/dashboard');
  view().innerHTML = `
    <h1>Welcome, ${esc(d.name)}</h1>
    <div class="cards">
      <div class="card"><div class="num">${esc(d.parcelCount)}</div><div class="lbl">Land records in my name</div></div>
      <div class="card"><div class="num">${esc(d.grievanceTotal)}</div><div class="lbl">Grievances submitted</div></div>
      <div class="card"><div class="num">${esc(d.grievanceOpen)}</div><div class="lbl">Unresolved grievances</div></div>
    </div>
    <div class="actions">
      <a class="btn" href="#/parcels">View all land records</a>
      <a class="btn btn-primary" href="#/submit">Submit a grievance</a>
    </div>
    <h2>Recent land records</h2>${parcelTable(d.recentParcels)}
    <h2>Recent grievances</h2>${grievanceTable(d.recentGrievances)}`;
}

async function pageProfile() {
  const u = await api.get('api/citizen/profile');
  view().innerHTML = `
    <h1>My Profile</h1>
    <div class="panel"><div class="grid2">
      ${kv('User ID', esc(u.userId))}${kv('Full name', esc(u.name))}
      ${kv('Email', esc(u.email))}${kv('Phone', esc(u.phone))}
      ${kv('Aadhaar', esc(u.aadhaarMasked))}${kv('Account type', esc(u.role))}
      ${kv('Registered on', esc(u.createdAt))}
    </div></div>
    <a class="btn" href="#/dashboard">Back to dashboard</a>`;
}

async function pageParcels() {
  view().innerHTML = `
    <h1>My Land Records</h1>
    <div class="row" style="margin-bottom:1rem"><div><label for="q" style="margin-top:0">Search</label>
      <input id="q" placeholder="Survey number, village or district"></div></div>
    <div id="list"></div>`;
  const load = async () => {
    const list = await api.get('api/citizen/parcels', { q: document.getElementById('q').value.trim() });
    document.getElementById('list').innerHTML = parcelTable(list);
  };
  document.getElementById('q').addEventListener('input', debounce(() => load().catch(e => toast(e.message, 'err')), 300));
  await load();
}

async function pageParcelDetail(id) {
  const d = await api.get('api/citizen/parcel', { id });
  const p = d.parcel;
  view().innerHTML = `
    <a class="back" href="#/parcels">&larr; Back to my land records</a>
    <h1>Land record ${esc(p.surveyNo)}</h1>
    <div class="panel"><div class="grid2">
      ${kv('Parcel ID', esc(p.parcelId))}${kv('Survey number', esc(p.surveyNo))}
      ${kv('District', esc(p.district))}${kv('Taluk', esc(p.taluk))}${kv('Village', esc(p.village))}
      ${kv('Area', esc(p.areaAcres) + ' acres')}${kv('Land type', esc(p.landType))}${kv('Land use', dash(p.landUse))}
      ${kv('Current owner', esc(p.ownerName))}${kv('Land status', badge('land', p.status))}
      ${kv('Recorded market value', money(p.marketValue))}
    </div>
    ${p.encumbrance ? `<h2>Encumbrance details</h2><div class="text-block">${esc(p.encumbrance)}</div>` : ''}</div>
    <h2>Ownership history</h2>${historyTable(d.history)}
    <h2>Yearly valuations</h2>${priceTable(d.prices)}
    <div class="actions"><a class="btn btn-primary" href="#/submit">Raise a grievance about this land</a></div>`;
}

async function pageGrievances() {
  view().innerHTML = `
    <h1>My Grievances</h1>
    <div class="actions"><a class="btn btn-primary" href="#/submit">Submit a new grievance</a></div>
    <div class="row" style="margin-bottom:1rem">
      <div><label for="q" style="margin-top:0">Search</label><input id="q" placeholder="Ticket, survey number or subject"></div>
      <div><label for="st" style="margin-top:0">Status</label>
        <select id="st"><option value="">All</option>${options('status')}</select></div>
    </div>
    <div id="list"></div>`;
  const load = async () => {
    const list = await api.get('api/citizen/grievances', {
      q: document.getElementById('q').value.trim(), status: document.getElementById('st').value });
    document.getElementById('list').innerHTML = grievanceTable(list);
  };
  document.getElementById('q').addEventListener('input', debounce(() => load().catch(e => toast(e.message, 'err')), 300));
  document.getElementById('st').addEventListener('change', () => load().catch(e => toast(e.message, 'err')));
  await load();
}

async function pageGrievanceDetail(id) {
  const g = await api.get('api/citizen/grievance', { id });
  view().innerHTML = `
    <a class="back" href="#/grievances">&larr; Back to my grievances</a>
    <h1>Ticket ${esc(g.ticketId)}</h1>
    <div class="panel"><div class="grid2">
      ${kv('Status', badge('status', g.status))}${kv('Priority', badge('priority', g.priority))}
      ${kv('Category', esc(label('category', g.category)))}
      ${kv('Land record', `<a href="#/parcel/${g.parcelId}">${esc(g.surveyNo)}</a> (${esc(g.village)})`)}
      ${kv('Submitted on', esc(g.createdAt))}${kv('Handled by', dash(g.adminName))}
      ${kv('Assigned on', dash(g.assignedAt))}${kv('Resolved on', dash(g.resolvedAt))}
    </div>
    <h2>Subject</h2><div>${esc(g.subject)}</div>
    <h2>Description</h2><div class="text-block">${esc(g.description)}</div>
    <h2>Admin remarks</h2><div class="text-block">${g.adminRemarks ? esc(g.adminRemarks) : 'No remarks yet.'}</div></div>`;
}

async function pageSubmit() {
  const parcels = await api.get('api/citizen/parcels');
  if (!parcels.length) {
    view().innerHTML = `<h1>Submit a Grievance</h1><div class="alert err">You need a land record in your name before you can file a grievance.</div>`;
    return;
  }
  view().innerHTML = `
    <h1>Submit a Grievance</h1>
    <div class="panel form-narrow">
      <form id="gForm" novalidate>
        <label for="parcelId" style="margin-top:0">Land record</label>
        <select id="parcelId" required>
          ${parcels.map(p => `<option value="${p.parcelId}">${esc(p.surveyNo)} - ${esc(p.village)}, ${esc(p.district)}</option>`).join('')}
        </select>
        <label for="category">Category</label>
        <select id="category" required>${options('category')}</select>
        <label for="subject">Subject</label>
        <input id="subject" maxlength="150" required placeholder="Short title of the problem">
        <label for="description">Description</label>
        <textarea id="description" maxlength="1000" required placeholder="Explain the issue clearly"></textarea>
        <label for="priority">Priority</label>
        <select id="priority">${options('priority', 'medium')}</select>
        <div id="formMsg"></div>
        <div class="actions"><button class="btn btn-primary" id="sendBtn" type="submit">Submit grievance</button></div>
      </form>
    </div>`;
  const form = document.getElementById('gForm');
  form.addEventListener('submit', async ev => {
    ev.preventDefault();
    const msg = document.getElementById('formMsg');
    msg.innerHTML = '';
    const subject = document.getElementById('subject').value.trim();
    const description = document.getElementById('description').value.trim();
    if (!subject) { msg.innerHTML = errorBox('Please enter a subject.'); return; }
    if (description.length < 10) { msg.innerHTML = errorBox('Please describe the issue in at least 10 characters.'); return; }
    const btn = document.getElementById('sendBtn');
    btn.disabled = true;
    try {
      const res = await api.post('api/citizen/grievance', {
        parcelId: document.getElementById('parcelId').value,
        category: document.getElementById('category').value,
        subject, description,
        priority: document.getElementById('priority').value
      });
      view().innerHTML = `<h1>Grievance submitted</h1>
        <div class="alert ok">Your grievance has been recorded. Your ticket number is <b>${esc(res.ticketId)}</b>.</div>
        <div class="actions"><a class="btn btn-primary" href="#/grievances">View my grievances</a>
        <a class="btn" href="#/dashboard">Back to dashboard</a></div>`;
    } catch (e) {
      msg.innerHTML = errorBox(e.message);
      btn.disabled = false;
    }
  });
}

startPortal('citizen', NAV, router);
