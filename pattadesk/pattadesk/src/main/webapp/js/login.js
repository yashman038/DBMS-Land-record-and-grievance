'use strict';
const form = document.getElementById('loginForm');
const msg = document.getElementById('msg');
const btn = document.getElementById('loginBtn');

document.getElementById('togglePw').addEventListener('click', e => {
  const pw = document.getElementById('password');
  const show = pw.type === 'password';
  pw.type = show ? 'text' : 'password';
  e.target.textContent = show ? 'Hide' : 'Show';
});

form.addEventListener('submit', async ev => {
  ev.preventDefault();
  msg.innerHTML = '';
  btn.disabled = true;
  btn.textContent = 'Signing in...';
  try {
    const res = await fetch('api/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'Accept': 'application/json' },
      body: new URLSearchParams({
        email: document.getElementById('email').value,
        password: document.getElementById('password').value
      }).toString()
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.error || 'Login failed.');
    location.href = body.redirect;
  } catch (e) {
    msg.innerHTML = '<div class="alert err" role="alert">' + esc(e.message) + '</div>';
    btn.disabled = false;
    btn.textContent = 'Login';
  }
});
