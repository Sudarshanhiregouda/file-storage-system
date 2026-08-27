import React, { useRef, useState } from "react";

function formatBytes(bytes) {
  if (!bytes) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

function getExtension(filename) {
  const parts = filename.split(".");
  return parts.length > 1 ? parts[parts.length - 1].toUpperCase() : "FILE";
}

function UploadForm({ onSubmit, loading, errors }) {
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [ownerName, setOwnerName] = useState("");
  const [dragging, setDragging] = useState(false);
  const fileInputRef = useRef(null);

  const pickFiles = (fileList) => {
    if (fileList && fileList.length > 0) {
      setSelectedFiles((prev) => {
        const names = new Set(prev.map((f) => f.name));
        const added = Array.from(fileList).filter((f) => !names.has(f.name));
        return [...prev, ...added];
      });
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    pickFiles(e.dataTransfer.files);
  };

  const removeFile = (e, name) => {
    e.stopPropagation();
    setSelectedFiles((prev) => prev.filter((f) => f.name !== name));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (selectedFiles.length === 0) return;

    await onSubmit(selectedFiles, ownerName.trim());
    setSelectedFiles([]);
    setOwnerName("");
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  return (
    <form className="panel form-panel" onSubmit={handleSubmit}>
      <h3 style={{ marginTop: 0 }}>
        Upload Files
        {selectedFiles.length > 1 && (
          <span style={{ fontSize: 13, fontWeight: 400, marginLeft: 8, color: "#666" }}>
            {selectedFiles.length} files selected
          </span>
        )}
      </h3>

      {/* Hidden real file input — multiple */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        style={{ display: "none" }}
        onChange={(e) => pickFiles(e.target.files)}
      />

      {/* Drop zone */}
      <div
        className={`drop-zone${dragging ? " dragging" : ""}`}
        onClick={() => fileInputRef.current.click()}
        onDrop={handleDrop}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
      >
        {selectedFiles.length === 0 ? (
          <div className="drop-placeholder">
            <span className="drop-icon">📂</span>
            <p>Click to browse or drag &amp; drop files here</p>
            <small>Multiple files supported — name, size and type are read automatically</small>
          </div>
        ) : (
          <div style={{ pointerEvents: "none" }}>
            {selectedFiles.map((file) => (
              <div key={file.name} className="file-preview" style={{ marginBottom: 6, pointerEvents: "all" }}>
                <span className="file-type-badge">{getExtension(file.name)}</span>
                <div className="file-preview-info">
                  <strong>{file.name}</strong>
                  <span className="file-meta">{formatBytes(file.size)}</span>
                </div>
                <button type="button" className="clear-btn" onClick={(e) => removeFile(e, file.name)}>✕</button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Owner name override — admin only; ignored for regular users (server forces current user) */}
      <div style={{ marginBottom: 12 }}>
        <input
          style={{ width: "100%" }}
          placeholder="Owner override (admin only, optional)"
          value={ownerName}
          onChange={(e) => setOwnerName(e.target.value)}
        />
      </div>

      {errors && Object.keys(errors).length > 0 && (
        <div className="error-box">
          {Object.entries(errors).map(([key, value]) => (
            <p key={key} style={{ margin: "2px 0" }}>{typeof value === "string" ? value : key}</p>
          ))}
        </div>
      )}

      <button type="submit" disabled={loading || selectedFiles.length === 0}>
        {loading
          ? "Uploading…"
          : selectedFiles.length > 1
          ? `Upload ${selectedFiles.length} Files`
          : "Upload File"}
      </button>
    </form>
  );
}

export default UploadForm;
