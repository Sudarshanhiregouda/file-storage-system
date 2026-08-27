import React, { useEffect, useState } from "react";
import AuthPanel from "./components/AuthPanel";
import UploadForm from "./components/UploadForm";
import FileTable from "./components/FileTable";
import StatsPanel from "./components/StatsPanel";
import {
  downloadFile, getAuditLog, getDeletedFiles, getFiles, getStats, getToken,
  logout, restoreFile, setToken, softDeleteFile, updateFile, uploadBinaryFile,
} from "./services/api";

function decodeToken(token) {
  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const claims = JSON.parse(json);
    return { username: claims.sub, role: claims.role };
  } catch {
    return null;
  }
}

function App() {
  const [user, setUser] = useState(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [activeTab, setActiveTab] = useState("Files");
  const [files, setFiles] = useState([]);
  const [deletedFiles, setDeletedFiles] = useState([]);
  const [stats, setStats] = useState({});
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [ownerInput, setOwnerInput] = useState("");
  const [ownerFilter, setOwnerFilter] = useState("");
  const [editingFile, setEditingFile] = useState(null);
  const [editForm, setEditForm] = useState({ file_name: "", owner_name: "" });
  const [editLoading, setEditLoading] = useState(false);
  const [auditLog, setAuditLog] = useState([]);

  const isAdmin = user?.role === "ADMIN";
  const tabs = isAdmin ? ["Files", "Stats", "Deleted Files", "Audit Log"] : ["Files"];

  useEffect(() => {
    if (window.location.pathname === "/oauth2/redirect") {
      const params = new URLSearchParams(window.location.search);
      const token = params.get("token");
      if (token) {
        setToken(token);
      }
      window.history.replaceState({}, "", "/");
    }

    const existingToken = getToken();
    if (existingToken) {
      setUser(decodeToken(existingToken));
    }
    setAuthChecked(true);
  }, []);

  useEffect(() => {
    if (!user) return;
    if (activeTab === "Files") {
      loadFiles(page, ownerFilter);
    }
    if (activeTab === "Stats") {
      loadStats();
    }
    if (activeTab === "Deleted Files") {
      loadDeletedFiles();
    }
    if (activeTab === "Audit Log") {
      loadAuditLog();
    }
  }, [user, activeTab, page, ownerFilter]);

  const handleAuthenticated = (data) => {
    setUser({ username: data.username, role: data.role });
    setActiveTab("Files");
  };

  const handleLogout = () => {
    logout();
    setUser(null);
  };

  const loadFiles = async (currentPage, owner) => {
    const data = await getFiles(currentPage, 10, owner);
    setFiles(data.content || []);
    setTotalPages(Math.max(data.totalPages || 1, 1));
  };

  const loadStats = async () => {
    const data = await getStats();
    setStats(data);
  };

  const loadDeletedFiles = async () => {
    const data = await getDeletedFiles();
    setDeletedFiles(data || []);
  };

  const loadAuditLog = async () => {
    const data = await getAuditLog();
    setAuditLog(data || []);
  };

  const handleUpload = async (selectedFiles, ownerOverride) => {
    setLoading(true);
    setErrors({});
    try {
      for (const file of selectedFiles) {
        await uploadBinaryFile(file, ownerOverride);
      }
      await loadFiles(0, ownerFilter);
      setPage(0);
    } catch (errorData) {
      setErrors(errorData);
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async (id, fileName) => {
    try {
      await downloadFile(id, fileName);
    } catch (e) {
      setErrors({ download: "Failed to download file" });
    }
  };

  const handleDelete = async (id) => {
    await softDeleteFile(id);
    await loadFiles(page, ownerFilter);
  };

  const handleRestore = async (id) => {
    await restoreFile(id);
    await loadDeletedFiles();
    await loadFiles(page, ownerFilter);
  };

  const handleOwnerFilter = () => {
    setPage(0);
    setOwnerFilter(ownerInput.trim());
  };

  const handleEditOpen = (file) => {
    setEditingFile(file);
    setEditForm({ file_name: file.file_name, owner_name: file.owner_name });
  };

  const handleEditClose = () => {
    setEditingFile(null);
    setEditForm({ file_name: "", owner_name: "" });
  };

  const handleEditSave = async () => {
    if (!editingFile) return;
    setEditLoading(true);
    try {
      await updateFile(editingFile.id, { file_name: editForm.file_name, owner_name: editForm.owner_name });
      await loadFiles(page, ownerFilter);
      handleEditClose();
    } finally {
      setEditLoading(false);
    }
  };

  const appStyles = (
    <style>{`
      :root {
        --bg: #f4f4ef;
        --ink: #1d1a17;
        --panel: #ffffff;
        --accent: #2f7f5f;
        --accent-soft: #dff2e7;
        --danger: #b9472c;
        --line: #d7d0c7;
      }
      * { box-sizing: border-box; }
      body { margin: 0; font-family: "Trebuchet MS", "Segoe UI", sans-serif; background: radial-gradient(circle at 10% 10%, #ffffff 0%, #f4f4ef 45%, #ece4d8 100%); color: var(--ink); }
      .drop-zone { border: 2px dashed var(--line); border-radius: 12px; padding: 20px 16px; cursor: pointer; transition: border-color 0.2s, background 0.2s; margin-bottom: 12px; }
      .drop-zone:hover, .drop-zone.dragging { border-color: var(--accent); background: var(--accent-soft); }
      .drop-placeholder { display: flex; flex-direction: column; align-items: center; color: #888; gap: 4px; pointer-events: none; }
      .drop-icon { font-size: 34px; }
      .drop-placeholder p { margin: 4px 0 0; font-size: 14px; }
      .drop-placeholder small { font-size: 12px; color: #aaa; }
      .file-preview { display: flex; align-items: center; gap: 10px; pointer-events: none; }
      .file-type-badge { background: var(--accent); color: #fff; border-radius: 6px; padding: 4px 8px; font-size: 11px; font-weight: 700; letter-spacing: 0.5px; white-space: nowrap; }
      .file-preview-info { flex: 1; overflow: hidden; }
      .file-preview-info strong { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
      .file-meta { font-size: 12px; color: #777; }
      .clear-btn { pointer-events: all; background: transparent; color: var(--danger); border: 1px solid var(--danger); padding: 3px 8px; border-radius: 6px; font-size: 12px; cursor: pointer; }
      .type-badge { background: #f0ede8; border-radius: 4px; padding: 2px 6px; font-size: 12px; font-weight: 600; }
      .status-chip { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
      .status-active { background: #dff2e7; color: #1f6e48; }
      .status-archived { background: #f0ede8; color: #66554a; }
      .btn-secondary { background: #e8e2da; color: var(--ink); }
      .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.35); display: flex; align-items: center; justify-content: center; z-index: 100; }
      .modal { background: #fff; border-radius: 16px; padding: 28px 24px; width: 420px; max-width: 92vw; box-shadow: 0 16px 40px rgba(0,0,0,0.2); }
      .modal h3 { margin: 0 0 16px; }
      .modal label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 4px; }
      .modal input { width: 100%; margin-bottom: 12px; }
      .modal-actions { display: flex; gap: 8px; margin-top: 4px; }
      .modal-actions button { flex: 1; }
      .app-shell { max-width: 1120px; margin: 0 auto; padding: 24px 16px 40px; }
      .header { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; margin-bottom: 16px; }
      h1 { margin: 0; letter-spacing: 0.5px; }
      .tabs { display: flex; gap: 8px; flex-wrap: wrap; }
      .tabs button { border: 1px solid var(--line); background: #fff; padding: 10px 14px; border-radius: 999px; cursor: pointer; color: var(--ink); font-weight: 500; }
      .tabs button.active { background: var(--accent); color: #fff; border-color: var(--accent); }
      .panel { background: var(--panel); border: 1px solid var(--line); border-radius: 14px; padding: 14px; margin-bottom: 14px; box-shadow: 0 8px 18px rgba(66, 55, 40, 0.07); }
      .form-panel h3 { margin-top: 0; }
      .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin-bottom: 10px; }
      input { padding: 10px; border-radius: 8px; border: 1px solid #bfb6ac; }
      button { border: none; border-radius: 8px; padding: 10px 12px; cursor: pointer; background: var(--accent); color: #fff; font-weight: 600; }
      button:disabled { opacity: 0.6; cursor: not-allowed; }
      button.danger { background: var(--danger); }
      table { width: 100%; border-collapse: collapse; }
      th, td { text-align: left; border-bottom: 1px solid #ece6de; padding: 10px 8px; font-size: 14px; }
      .center { text-align: center; }
      .pagination { display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 12px; }
      .filter-row { display: flex; gap: 8px; margin-bottom: 12px; }
      .filter-row input { max-width: 250px; }
      .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin-bottom: 14px; }
      .card { background: var(--accent-soft); border-radius: 10px; padding: 14px; }
      .card h4 { margin: 0 0 6px; font-size: 14px; text-transform: uppercase; letter-spacing: 0.5px; }
      .card p { margin: 0; font-size: 22px; font-weight: bold; }
      .error-box { border: 1px solid #e2b0a6; background: #fff0ec; color: #842f19; border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; }
      .hint { margin-top: 0; color: #5e554c; font-size: 14px; }
      .action-chip { background: #2f7f5f1a; color: #1f6e48; border-radius: 4px; padding: 2px 8px; font-size: 12px; font-weight: 700; }
      .btn-restore { background: #2f5f9f; color: #fff; }
      .user-badge { font-size: 13px; color: #5e554c; display: flex; align-items: center; gap: 10px; }
      .role-chip { background: #2f5f9f1a; color: #2f5f9f; border-radius: 999px; padding: 2px 10px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
    `}</style>
  );

  if (!authChecked) {
    return <div className="app-shell">{appStyles}</div>;
  }

  if (!user) {
    return (
      <div className="app-shell">
        {appStyles}
        <AuthPanel onAuthenticated={handleAuthenticated} />
      </div>
    );
  }

  return (
    <div className="app-shell">
      {appStyles}

      <header className="header">
        <h1>File Storage Manager</h1>
        <nav className="tabs">
          {tabs.map((tab) => (
            <button key={tab} className={activeTab === tab ? "active" : ""} onClick={() => setActiveTab(tab)}>
              {tab}
            </button>
          ))}
        </nav>
        <div className="user-badge">
          {user.username} <span className="role-chip">{user.role}</span>
          <button className="btn-secondary" onClick={handleLogout}>Log Out</button>
        </div>
      </header>

      {activeTab === "Files" && (
        <div>
          <UploadForm onSubmit={handleUpload} loading={loading} errors={errors} />
          <div className="panel">
            {isAdmin && (
              <div className="filter-row">
                <input
                  placeholder="Filter by owner name"
                  value={ownerInput}
                  onChange={(event) => setOwnerInput(event.target.value)}
                />
                <button onClick={handleOwnerFilter}>Filter</button>
              </div>
            )}
            <FileTable files={files} onDelete={handleDelete} onEdit={handleEditOpen} onDownload={handleDownload} />
            <div className="pagination">
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</button>
              <span>Page {page + 1} of {totalPages}</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Next</button>
            </div>
          </div>
        </div>
      )}

      {activeTab === "Stats" && <StatsPanel stats={stats} />}

      {/* Edit Modal */}
      {editingFile && (
        <div className="modal-overlay" onClick={handleEditClose}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>Edit File #{editingFile.id}</h3>
            <label>File Name</label>
            <input
              value={editForm.file_name}
              onChange={(e) => setEditForm((f) => ({ ...f, file_name: e.target.value }))}
            />
            <label>Owner Name</label>
            <input
              value={editForm.owner_name}
              onChange={(e) => setEditForm((f) => ({ ...f, owner_name: e.target.value }))}
              disabled={!isAdmin}
            />
            <div className="modal-actions">
              <button className="btn-secondary" onClick={handleEditClose}>Cancel</button>
              <button onClick={handleEditSave} disabled={editLoading}>
                {editLoading ? "Saving…" : "Save Changes"}
              </button>
            </div>
          </div>
        </div>
      )}

      {activeTab === "Deleted Files" && (
        <div className="panel">
          <p className="hint">These files are archived (soft-deleted). Click Restore to make them active again.</p>
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>File Name</th>
                <th>Type</th>
                <th>Owner</th>
                <th>Archived At</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {deletedFiles.length === 0 ? (
                <tr>
                  <td colSpan="6" className="center">No archived files.</td>
                </tr>
              ) : (
                deletedFiles.map((file) => (
                  <tr key={file.id}>
                    <td>{file.id}</td>
                    <td>{file.file_name}</td>
                    <td>{file.file_type}</td>
                    <td>{file.owner_name}</td>
                    <td>{file.deleted_at ? new Date(file.deleted_at).toLocaleString() : "-"}</td>
                    <td>
                      <button className="btn-restore" onClick={() => handleRestore(file.id)}>Restore</button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {activeTab === "Audit Log" && (
        <div className="panel">
          <p className="hint">Last 50 actions recorded across all files</p>
          <table>
            <thead>
              <tr>
                <th>File ID</th>
                <th>Action</th>
                <th>Performed By</th>
                <th>Timestamp</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {auditLog.length === 0 ? (
                <tr>
                  <td colSpan="5" className="center">No audit records yet.</td>
                </tr>
              ) : (
                auditLog.map((log) => (
                  <tr key={log.id}>
                    <td>{log.fileId}</td>
                    <td><span className="action-chip">{log.action}</span></td>
                    <td>{log.performedBy}</td>
                    <td>{new Date(log.performedAt).toLocaleString()}</td>
                    <td style={{ fontSize: 12, color: "#666" }}>{log.details}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default App;
