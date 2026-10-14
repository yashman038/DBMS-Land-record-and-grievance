'use strict';
/* Admin portal: dashboard, land records, grievances, users. */

const NAV = [
  { route: 'dashboard', label: 'Dashboard' },
  { route: 'parcels', label: 'Land Records' },
  { route: 'grievances', label: 'Grievances' },
  { route: 'users', label: 'User Management' }
];
const ACTIVE = { dashboard: 'dashboard', parcels: 'parcels', parcel: 'parcels',
                 grievances: 'grievances', grievance: 'grievances', users: 'users' };
let ME = null;

async function router() {
  const { page, arg } = currentRoute('dashboard');
  setActive(ACTIVE[page]);
  view().innerHTML = '<p class="muted">Loading...</p>';
  try {
    switch (page) {
      case 'dashboard': await pageDashboard(); break;
      case 'parcels': await pageParcels(); break;
      case 'parcel': await pageParcelDetail(arg); break;
      case 'grievances': await pageGrievances(); break;
      case 'grievance': await pageGrievanceDetail(arg); break;
      case 'users': await pageUsers(); break;
      default: location.hash = '#/dashboard';
    }
  } catch (e) {
    view().innerHTML = errorBox(e.message);
  }
}

function grievanceRows(list) {
  return list.map(g => `<tr><td>${esc(g.ticketId)}</td><td>${esc(g.citizenName)}</td><td>${esc(g.surveyNo)}</td>
    <td>${esc(label('category', g.category))}</td><td>${badge('priority', g.priority)}</td><td>${esc(g.createdAt)}</td>
    <td>${badge('status', g.status)}</td><td><a class="btn btn-sm" href="#/grievance/${g.grievanceId}">Review</a></td></tr>`);
}
const GRIEVANCE_HEAD = ['Ticket', 'Citizen', 'Survey no.', 'Category', 'Priority', 'Submitted', 'Status', ''];

// ---------- dashboard ----------
async function pageDashboard() {
  const d = await api.get('api/admin/dashboard');
  const s = d.byStatus;
  const card = (n, l) => `<div class="card"><div class="num">${esc(n)}</div><div class="lbl">${esc(l)}</div></div>`;
  view().innerHTML = `
    <h1>Admin Dashboard</h1>
    <div class="cards">
      ${card(d.citizens, 'Registered citizens')}${card(d.parcels, 'Total land parcels')}
      ${card(s.submitted, 'Submitted (unassigned)')}${card(s.assigned, 'Assigned')}
      ${card(s.in_progress, 'In progress')}${card(s.resolved, 'Resolved')}${card(s.rejected, 'Rejected')}
    </div>
    <div class="actions"><a class="btn" href="#/parcels">Manage land records</a>
      <a class="btn btn-primary" href="#/grievances">Manage grievances</a></div>
    <h2>Recent grievances</h2>
    ${table(GRIEVANCE_HEAD, grievanceRows(d.recentGrievances), 'No grievances have been submitted yet.')}
    <h2>Database connection</h2>
    <div class="panel"><button class="btn" id="dbBtn">Check Oracle connection</button>
      <div id="dbOut" class="small" style="margin-top:.6rem"></div></div>`;
  document.getElementById('dbBtn').addEventListener('click', ev => withButton(ev.target, async () => {
    const r = await api.get('api/admin/dbtest');
    document.getElementById('dbOut').innerHTML = `<div class="alert ok">Connected. Schema user: <b>${esc(r.schemaUser)}</b>,
      service: <b>${esc(r.service)}</b>, database: <b>${esc(r.database)}</b></div>`;
  }));
}

// ---------- land records ----------
async function pageParcels() {
  view().innerHTML = `
    <h1>Land Records</h1>
    <div class="row" style="margin-bottom:1rem">
      <div><label for="q" style="margin-top:0">Search</label><input id="q" placeholder="Survey no., village, district or owner"></div>
      <div><label for="district" style="margin-top:0">District</label><select id="district"><option value="">All</option></select></div>
      <div><label for="village" style="margin-top:0">Village</label><select id="village"><option value="">All</option></select></div>
      <div><label for="status" style="margin-top:0">Land status</label>
        <select id="status"><option value="">All</option>${options('land')}</select></div>
    </div>
    <div id="list"></div>`;
  let filled = false;
  const load = async () => {
    const r = await api.get('api/admin/parcels', {
      q: document.getElementById('q').value.trim(), district: document.getElementById('district').value,
      village: document.getElementById('village').value, status: document.getElementById('status').value });
    if (!filled) {
      document.getElementById('district').insertAdjacentHTML('beforeend', r.districts.map(d => `<option>${esc(d)}</option>`).join(''));
      document.getElementById('village').insertAdjacentHTML('beforeend', r.villages.map(v => `<option>${esc(v)}</option>`).join(''));
      filled = true;
    }
    document.getElementById('list').innerHTML = table(
      ['ID', 'Survey no.', 'District', 'Village', 'Current owner', 'Area', 'Type', 'Status', ''],
      r.parcels.map(p => `<tr><td>${esc(p.parcelId)}</td><td>${esc(p.surveyNo)}</td><td>${esc(p.district)}</td>
        <td>${esc(p.village)}</td><td>${esc(p.ownerName)}</td><td>${esc(p.areaAcres)} ac</td><td>${esc(p.landType)}</td>
        <td>${badge('land', p.status)}</td><td><a class="btn btn-sm" href="#/parcel/${p.parcelId}">View</a></td></tr>`),
      'No land records match these filters.');
  };
  const reload = () => load().catch(e => toast(e.message, 'err'));
  document.getElementById('q').addEventListener('input', debounce(reload, 300));
  ['district', 'village', 'status'].forEach(id => document.getElementById(id).addEventListener('change', reload));
  await load();
}

async function pageParcelDetail(id) {
  const [d, users] = await Promise.all([api.get('api/admin/parcel', { id }), api.get('api/admin/users')]);
  const p = d.parcel;
  const owners = users.filter(u => u.role === 'citizen' && u.userId !== p.ownerId);
  const today = new Date().toISOString().slice(0, 10);
  view().innerHTML = `
    <a class="back" href="#/parcels">&larr; Back to land records</a>
    <h1>Land record ${esc(p.surveyNo)}</h1>
    <div class="panel"><div class="grid2">
      ${kv('Parcel ID', esc(p.parcelId))}${kv('Survey number', esc(p.surveyNo))}
      ${kv('District', esc(p.district))}${kv('Taluk', esc(p.taluk))}${kv('Village', esc(p.village))}
      ${kv('Area', esc(p.areaAcres) + ' acres')}${kv('Land type', esc(p.landType))}${kv('Land use', dash(p.landUse))}
      ${kv('Current owner', esc(p.ownerName))}${kv('Land status', badge('land', p.status))}
      ${kv('Recorded market value', money(p.marketValue))}
      ${kv('Coordinates', p.latitude == null ? '&mdash;' : esc(p.latitude) + ', ' + esc(p.longitude))}
    </div>
    ${p.encumbrance ? `<h2>Encumbrance details</h2><div class="text-block">${esc(p.encumbrance)}</div>` : ''}</div>

    <h2>Update land status</h2>
    <div class="panel form-narrow">
      <label for="newStatus" style="margin-top:0">Land status</label>
      <select id="newStatus">${options('land', p.status)}</select>
      <label for="enc">Encumbrance / dispute details <span class="muted small">(required unless status is Clear)</span></label>
      <textarea id="enc" maxlength="300">${esc(p.encumbrance || '')}</textarea>
      <div class="actions"><button class="btn btn-primary" id="statusBtn">Update status</button></div>
    </div>

    <h2>Transfer ownership</h2>
    <div class="panel form-narrow">
      <p class="muted small" style="margin-top:0">The current ownership period is closed and a new one is added. Old history is never edited.</p>
      <label for="newOwner" style="margin-top:0">New owner</label>
      <select id="newOwner">${owners.map(u => `<option value="${u.userId}">${esc(u.name)} (ID ${u.userId})</option>`).join('')}</select>
      <label for="ttype">Transfer type</label>
      <select id="ttype">${['sale', 'inheritance', 'gift', 'partition', 'court_order'].map(k => `<option value="${k}">${esc(label('transfer', k))}</option>`).join('')}</select>
      <label for="deed">Deed number</label><input id="deed" maxlength="40">
      <label for="amount">Consideration amount (INR)</label><input id="amount" type="number" min="0" step="1">
      <label for="tdate">Transfer date</label><input id="tdate" type="date" max="${today}" value="${today}">
      <div class="actions"><button class="btn btn-primary" id="transferBtn"${owners.length ? '' : ' disabled'}>Transfer ownership</button></div>
    </div>

    <h2>Ownership history</h2>${historyTable(d.history)}
    <h2>Yearly valuations</h2>${priceTable(d.prices)}`;

  document.getElementById('statusBtn').addEventListener('click', ev => withButton(ev.target, async () => {
    const status = document.getElementById('newStatus').value;
    if (!await confirmBox(`Change the land status to "${label('land', status)}"?`, 'Update')) return;
    await api.post('api/admin/parcel/status', { parcelId: p.parcelId, status, encumbrance: document.getElementById('enc').value });
    toast('Land status updated.');
    await pageParcelDetail(id);
  }));
  document.getElementById('transferBtn').addEventListener('click', ev => withButton(ev.target, async () => {
    const sel = document.getElementById('newOwner');
    const name = sel.options[sel.selectedIndex].text;
    if (!await confirmBox(`Transfer this parcel to ${name}? This adds a permanent ownership record.`, 'Transfer')) return;
    await api.post('api/admin/parcel/transfer', {
      parcelId: p.parcelId, newOwnerId: sel.value, transferType: document.getElementById('ttype').value,
      deedNo: document.getElementById('deed').value, amount: document.getElementById('amount').value,
      transferDate: document.getElementById('tdate').value });
    toast('Ownership transferred.');
    await pageParcelDetail(id);
  }));
}

// ---------- grievances ----------
async function pageGrievances() {
  view().innerHTML = `
    <h1>Grievances</h1>
    <div class="row" style="margin-bottom:1rem">
      <div><label for="q" style="margin-top:0">Search</label><input id="q" placeholder="Ticket, citizen, survey number or subject"></div>
      <div><label for="st" style="margin-top:0">Status</label><select id="st"><option value="">All</option>${options('status')}</select></div>
    </div>
    <div id="list"></div>`;
  const load = async () => {
    const list = await api.get('api/admin/grievances', { q: document.getElementById('q').value.trim(), status: document.getElementById('st').value });
    document.getElementById('list').innerHTML = table(GRIEVANCE_HEAD, grievanceRows(list), 'No grievances match these filters.');
  };
  const reload = () => load().catch(e => toast(e.message, 'err'));
  document.getElementById('q').addEventListener('input', debounce(reload, 300));
  document.getElementById('st').addEventListener('change', reload);
  await load();
}

async function pageGrievanceDetail(id) {
  const [g, users] = await Promise.all([api.get('api/admin/grievance', { id }), api.get('api/admin/users')]);
  const admins = users.filter(u => u.role === 'admin');
  const open = ['submitted', 'assigned', 'in_progress'].includes(g.status);
  const canResolve = ['assigned', 'in_progress'].includes(g.status);
  view().innerHTML = `
    <a class="back" href="#/grievances">&larr; Back to grievances</a>
    <h1>Ticket ${esc(g.ticketId)}</h1>
    <div class="panel">
      <h2 style="margin-top:0">Grievance</h2><div class="grid2">
        ${kv('Status', badge('status', g.status))}${kv('Priority', badge('priority', g.priority))}
        ${kv('Category', esc(label('category', g.category)))}${kv('Submitted on', esc(g.createdAt))}
        ${kv('Assigned to', dash(g.adminName))}${kv('Assigned on', dash(g.assignedAt))}
        ${kv('Resolved on', dash(g.resolvedAt))}
      </div>
      <h2>Subject</h2><div>${esc(g.subject)}</div>
      <h2>Description</h2><div class="text-block">${esc(g.description)}</div>
      <h2>Admin remarks</h2><div class="text-block">${g.adminRemarks ? esc(g.adminRemarks) : 'No remarks yet.'}</div>
    </div>
    <div class="panel"><h2 style="margin-top:0">Citizen</h2><div class="grid2">
      ${kv('Name', esc(g.citizenName))}${kv('Email', esc(g.citizenEmail))}${kv('Phone', esc(g.citizenPhone))}
    </div></div>
    <div class="panel"><h2 style="margin-top:0">Land record</h2><div class="grid2">
      ${kv('Survey number', esc(g.surveyNo))}${kv('Village', esc(g.village))}
      ${kv('Parcel', `<a href="#/parcel/${g.parcelId}">Open land record</a>`)}
    </div></div>
    ${open ? `
    <div class="panel form-narrow"><h2 style="margin-top:0">Actions</h2>
      <label for="assignee" style="margin-top:0">Assign to admin</label>
      <select id="assignee">${admins.map(a => `<option value="${a.userId}"${a.userId === g.assignedTo ? ' selected' : ''}>${esc(a.name)}</option>`).join('')}</select>
      <div class="actions"><button class="btn btn-primary" id="assignBtn">${g.status === 'submitted' ? 'Assign' : 'Reassign'}</button>
      ${g.status === 'assigned' ? '<button class="btn" id="progressBtn">Move to In Progress</button>' : ''}</div>
      ${canResolve ? `
      <label for="remarks">Admin remarks <span class="muted small">(required to resolve or reject)</span></label>
      <textarea id="remarks" maxlength="1000"></textarea>
      <div class="actions"><button class="btn btn-primary" id="resolveBtn">Resolve</button>
      <button class="btn btn-danger" id="rejectBtn">Reject</button></div>`
      : '<p class="muted small">Assign this grievance before it can be resolved or rejected.</p>'}
    </div>` : '<div class="alert ok">This grievance is closed.</div>'}`;

  const act = (btnId, work) => {
    const b = document.getElementById(btnId);
    if (b) b.addEventListener('click', ev => withButton(ev.target, async () => {
      const done = await work();                 // work() returns false when the admin pressed Cancel
      if (done !== false) await pageGrievanceDetail(id);
    }));
  };
  act('assignBtn', async () => {
    const sel = document.getElementById('assignee');
    if (!await confirmBox(`Assign this grievance to ${sel.options[sel.selectedIndex].text}?`, 'Assign')) return false;
    await api.post('api/admin/grievance/assign', { grievanceId: g.grievanceId, assigneeId: sel.value });
    toast('Grievance assigned.');
  });
  act('progressBtn', async () => {
    await api.post('api/admin/grievance/progress', { grievanceId: g.grievanceId });
    toast('Marked as In Progress.');
  });
  const decide = outcome => async () => {
    const remarks = document.getElementById('remarks').value.trim();
    if (!remarks) throw new Error('Enter admin remarks first.');
    if (!await confirmBox(`Mark this grievance as ${label('status', outcome)}? Citizens will see your remarks.`, label('status', outcome))) return false;
    await api.post('api/admin/grievance/resolve', { grievanceId: g.grievanceId, remarks, outcome });
    toast('Grievance ' + outcome + '.');
  };
  act('resolveBtn', decide('resolved'));
  act('rejectBtn', decide('rejected'));
}

// ---------- users ----------
async function pageUsers() {
  const users = await api.get('api/admin/users');
  view().innerHTML = `<h1>User Management</h1>` + table(
    ['ID', 'Name', 'Email', 'Phone', 'Role', 'Registered', 'Parcels', 'Grievances'],
    users.map(u => `<tr><td>${esc(u.userId)}</td><td>${esc(u.name)}</td><td>${esc(u.email)}</td><td>${esc(u.phone)}</td>
      <td><span class="pill">${esc(u.role)}</span></td><td>${esc(u.createdAt)}</td><td>${esc(u.parcelCount)}</td><td>${esc(u.grievanceCount)}</td></tr>`),
    'No users found.');
}

startPortal('admin', NAV, router).then(me => { ME = me; });
