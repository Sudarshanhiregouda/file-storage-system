const API_ROOT = "http://localhost:8080";
const API_BASE_URL = `${API_ROOT}/api/files`;
const AUTH_BASE_URL = `${API_ROOT}/api/auth`;

const TOKEN_KEY = "fs_token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

function authHeaders(extra = {}) {
  const token = getToken();
  return token ? { ...extra, Authorization: `Bearer ${token}` } : extra;
}

async function handleJsonResponse(response) {
  if (response.status === 401) {
    clearToken();
    throw { error: "Session expired, please log in again" };
  }
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({ error: response.statusText }));
    throw errorData;
  }
  return response.json();
}

export async function register(username, email, password) {
  const response = await fetch(`${AUTH_BASE_URL}/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, email, password }),
  });
  const data = await handleJsonResponse(response);
  setToken(data.token);
  return data;
}

export async function login(username, password) {
  const response = await fetch(`${AUTH_BASE_URL}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  const data = await handleJsonResponse(response);
  setToken(data.token);
  return data;
}

export function logout() {
  clearToken();
}

export function googleLoginUrl() {
  return `${API_ROOT}/oauth2/authorization/google`;
}

export async function createFile(payload) {
  const response = await fetch(API_BASE_URL, {
    method: "POST",
    headers: authHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(payload)
  });
  return handleJsonResponse(response);
}

export async function uploadBinaryFile(file, ownerName) {
  const formData = new FormData();
  formData.append("file", file);
  if (ownerName) formData.append("owner_name", ownerName);

  const response = await fetch(`${API_BASE_URL}/upload`, {
    method: "POST",
    headers: authHeaders(),
    body: formData,
  });
  return handleJsonResponse(response);
}

export async function downloadFile(id, suggestedName) {
  const response = await fetch(`${API_BASE_URL}/${id}/download`, {
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error("Failed to download file");
  }
  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = suggestedName || `file-${id}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

export async function getFiles(page = 0, size = 10, owner = "") {
  const ownerQuery = owner ? `&owner=${encodeURIComponent(owner)}` : "";
  const response = await fetch(`${API_BASE_URL}?page=${page}&size=${size}${ownerQuery}`, {
    headers: authHeaders(),
  });
  return handleJsonResponse(response);
}

export async function softDeleteFile(id) {
  const response = await fetch(`${API_BASE_URL}/${id}`, {
    method: "DELETE",
    headers: authHeaders(),
  });
  return handleJsonResponse(response);
}

export async function getStats() {
  const response = await fetch(`${API_BASE_URL}/stats`, { headers: authHeaders() });
  return handleJsonResponse(response);
}

export async function updateFile(id, payload) {
  const response = await fetch(`${API_BASE_URL}/${id}`, {
    method: "PUT",
    headers: authHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(payload),
  });
  return handleJsonResponse(response);
}

export async function getDeletedFiles() {
  const response = await fetch(`${API_BASE_URL}/deleted`, { headers: authHeaders() });
  return handleJsonResponse(response);
}

export async function restoreFile(id) {
  const response = await fetch(`${API_BASE_URL}/${id}/restore`, {
    method: "PUT",
    headers: authHeaders(),
  });
  return handleJsonResponse(response);
}

export async function uploadMultipleFiles(payloads) {
  const response = await fetch(`${API_BASE_URL}/batch`, {
    method: "POST",
    headers: authHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(payloads),
  });
  return handleJsonResponse(response);
}

export async function getAuditLog() {
  const response = await fetch(`${API_BASE_URL}/audit`, { headers: authHeaders() });
  return handleJsonResponse(response);
}
