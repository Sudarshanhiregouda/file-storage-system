import React, { useState } from "react";
import { googleLoginUrl, login, register } from "../services/api";

function AuthPanel({ onAuthenticated }) {
  const [mode, setMode] = useState("login");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const data = mode === "login"
        ? await login(username, password)
        : await register(username, email, password);
      onAuthenticated(data);
    } catch (err) {
      setError(err.error || Object.values(err)[0] || "Something went wrong");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="panel" style={{ maxWidth: 380, margin: "60px auto" }}>
      <h2 style={{ marginTop: 0, textAlign: "center" }}>File Storage Manager</h2>
      <div className="tabs" style={{ justifyContent: "center", marginBottom: 16 }}>
        <button className={mode === "login" ? "active" : "btn-secondary"} onClick={() => setMode("login")}>
          Log In
        </button>
        <button className={mode === "register" ? "active" : "btn-secondary"} onClick={() => setMode("register")}>
          Register
        </button>
      </div>

      <form onSubmit={handleSubmit}>
        <input
          style={{ width: "100%", marginBottom: 10 }}
          placeholder="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />
        {mode === "register" && (
          <input
            type="email"
            style={{ width: "100%", marginBottom: 10 }}
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        )}
        <input
          type="password"
          style={{ width: "100%", marginBottom: 10 }}
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <div className="error-box">{error}</div>}
        <button type="submit" disabled={loading} style={{ width: "100%" }}>
          {loading ? "Please wait…" : mode === "login" ? "Log In" : "Create Account"}
        </button>
      </form>

      <div style={{ textAlign: "center", margin: "16px 0 4px", color: "#888", fontSize: 13 }}>or</div>
      <a href={googleLoginUrl()} style={{ display: "block" }}>
        <button type="button" className="btn-secondary" style={{ width: "100%" }}>
          Continue with Google
        </button>
      </a>
      <p className="hint" style={{ textAlign: "center", marginTop: 12 }}>
        First run seeds an <strong>admin</strong> account (see backend ADMIN_USERNAME/ADMIN_PASSWORD env vars).
      </p>
    </div>
  );
}

export default AuthPanel;
