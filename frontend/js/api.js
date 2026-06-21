const GATEWAY_URL = 'http://localhost:8080';
const AUTH_SERVICE_URL = GATEWAY_URL;
const AUDIO_SERVICE_URL = GATEWAY_URL;

// LocalStorage Helpers
function getToken() {
  return localStorage.getItem('jwt_token');
}

function getRole() {
  return localStorage.getItem('user_role');
}

function getUsername() {
  return localStorage.getItem('user_username');
}

function getSessionId() {
  return localStorage.getItem('admin_session_id');
}

function setAuth(token, role, username, sessionId = null) {
  localStorage.setItem('jwt_token', token);
  localStorage.setItem('user_role', role);
  localStorage.setItem('user_username', username);
  if (sessionId) {
    localStorage.setItem('admin_session_id', sessionId);
  } else {
    localStorage.removeItem('admin_session_id');
  }
}

function clearAuth() {
  localStorage.removeItem('jwt_token');
  localStorage.removeItem('user_role');
  localStorage.removeItem('user_username');
  localStorage.removeItem('admin_session_id');
}

// Check authorization on page load
function requireAuth(allowedRoles = []) {
  const token = getToken();
  const role = getRole();

  if (!token || !role) {
    clearAuth();
    window.location.href = 'login.html';
    return null;
  }

  if (allowedRoles.length > 0 && !allowedRoles.includes(role)) {
    // Redirect to correct dashboard based on role
    if (role === 'ADMIN') window.location.href = 'dashboard-admin.html';
    else if (role === 'ARTIST') window.location.href = 'dashboard-artist.html';
    else if (role === 'LISTENER') window.location.href = 'dashboard-listener.html';
    else window.location.href = 'login.html';
    return null;
  }

  return { token, role, username: getUsername(), sessionId: getSessionId() };
}

// API Call Wrapper with Interceptors
async function apiCall(url, options = {}) {
  const token = getToken();
  
  // Initialize headers
  options.headers = options.headers || {};
  if (token && !options.headers['Authorization']) {
    options.headers['Authorization'] = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, options);
    
    // Auto logout on unauthorized or banned intercept
    if (response.status === 401) {
      showToast('Session expired. Logging out...', 'danger');
      setTimeout(() => {
        clearAuth();
        window.location.href = 'login.html';
      }, 1500);
      throw new Error('Unauthorized');
    }
    
    if (response.status === 403) {
      // Could be account suspended (ban check) or unauthorized role
      const data = await response.json().catch(() => ({}));
      const errorMsg = data.error || data.message || 'Access Denied (403)';
      showToast(errorMsg, 'danger');
      
      if (errorMsg.toLowerCase().includes('suspend') || errorMsg.toLowerCase().includes('ban')) {
        setTimeout(() => {
          clearAuth();
          window.location.href = 'login.html';
        }, 2000);
      }
      throw new Error(errorMsg);
    }

    return response;
  } catch (error) {
    if (error.message !== 'Unauthorized' && !error.message.includes('403')) {
      console.error('API Call Error:', error);
    }
    throw error;
  }
}

// Toast Notifications System
function showToast(message, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span class="toast-message">${message}</span>
  `;
  container.appendChild(toast);

  // Trigger animation frame
  requestAnimationFrame(() => {
    toast.classList.add('show');
  });

  // Remove toast after 4s
  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => {
      toast.remove();
    }, 350);
  }, 4000);
}

// Global Logout Handler
async function performLogout() {
  const sessionId = getSessionId();
  const logoutUrl = `${AUTH_SERVICE_URL}/api/auth/logout` + (sessionId ? `?sessionId=${sessionId}` : '');
  
  try {
    await apiCall(logoutUrl, { method: 'POST' });
  } catch (e) {
    console.warn('Logout request failed, cleaning local storage anyway', e);
  } finally {
    clearAuth();
    window.location.href = 'login.html';
  }
}

// Utility formats
function formatDuration(seconds) {
  if (!seconds || isNaN(seconds)) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

function formatCurrency(amount) {
  if (amount === undefined || amount === null) return '0';
  return parseFloat(amount).toLocaleString('id-ID', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}
