import React from "react";

function toMb(bytes) {
  return (bytes / (1024 * 1024)).toFixed(2);
}

function toKb(bytes) {
  return (bytes / 1024).toFixed(2);
}

function StatsPanel({ stats }) {
  return (
    <div>
      <div className="cards">
        <div className="card">
          <h4>Total Files</h4>
          <p>{stats.total_file_count || 0}</p>
        </div>
        <div className="card">
          <h4>Total Storage Used</h4>
          <p>{toMb(stats.total_storage_used || 0)} MB</p>
        </div>
        <div className="card">
          <h4>Deleted Files</h4>
          <p>{stats.deleted_file_count || 0}</p>
        </div>
        <div className="card">
          <h4>File Types</h4>
          <p>{stats.file_type_count || 0}</p>
        </div>
      </div>

      <div className="panel">
        <h3>Storage by File Type</h3>
        <table>
          <thead>
            <tr>
              <th>File Type</th>
              <th>Total Files</th>
              <th>Total Size (MB)</th>
            </tr>
          </thead>
          <tbody>
            {(stats.storage_by_type || []).length === 0 ? (
              <tr>
                <td colSpan="3" className="center">No data available.</td>
              </tr>
            ) : (
              stats.storage_by_type.map((row) => (
                <tr key={row.file_type}>
                  <td>{row.file_type}</td>
                  <td>{row.total_files}</td>
                  <td>{toMb(row.total_size)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="panel">
        <h3>Top 5 Largest Files</h3>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Type</th>
              <th>Size (KB)</th>
              <th>Owner</th>
            </tr>
          </thead>
          <tbody>
            {(stats.top_largest_files || []).length === 0 ? (
              <tr>
                <td colSpan="4" className="center">No files available.</td>
              </tr>
            ) : (
              stats.top_largest_files.map((file) => (
                <tr key={file.id}>
                  <td>{file.file_name}</td>
                  <td>{file.file_type}</td>
                  <td>{toKb(file.file_size)}</td>
                  <td>{file.owner_name}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default StatsPanel;
