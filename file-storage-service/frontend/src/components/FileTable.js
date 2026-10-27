import React from "react";

function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function formatSize(bytes) {
  if (!bytes) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

function StatusChip({ status }) {
  const cls = status === "ACTIVE" ? "status-chip status-active" : "status-chip status-archived";
  return <span className={cls}>{status}</span>;
}

const INDEX_LABELS = {
  INDEXED: "Searchable",
  PENDING: "Indexing…",
  FAILED: "Index failed",
  UNSUPPORTED: "Not searchable",
};

function IndexChip({ status }) {
  if (!status) return null;
  return (
    <span className={`index-chip index-${status.toLowerCase()}`} title={`Search index: ${status}`}>
      {INDEX_LABELS[status] || status}
    </span>
  );
}

function FileTable({ files, onDelete, onEdit, onDownload }) {
  return (
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>File Name</th>
          <th>Type</th>
          <th>Size</th>
          <th>Owner</th>
          <th>Status</th>
          <th>Uploaded At</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {files.length === 0 ? (
          <tr>
            <td colSpan="8" className="center">No files found.</td>
          </tr>
        ) : (
          files.map((file) => (
            <tr key={file.id}>
              <td>{file.id}</td>
              <td>{file.file_name}</td>
              <td><span className="type-badge">{file.file_type}</span></td>
              <td>{formatSize(file.file_size)}</td>
              <td>{file.owner_name}</td>
              <td>
                <StatusChip status={file.status || "ACTIVE"} />
                <IndexChip status={file.index_status} />
              </td>
              <td>{formatDate(file.created_at)}</td>
              <td style={{ display: "flex", gap: 6 }}>
                {file.downloadable && (
                  <button className="btn-restore" onClick={() => onDownload(file.id, file.file_name)}>Download</button>
                )}
                <button className="btn-secondary" onClick={() => onEdit(file)}>Edit</button>
                <button className="danger" onClick={() => onDelete(file.id)}>Archive</button>
              </td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}

export default FileTable;
