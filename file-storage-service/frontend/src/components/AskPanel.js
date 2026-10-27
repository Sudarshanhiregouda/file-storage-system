import React, { useState } from "react";
import { askQuestion } from "../services/api";

const NOT_FOUND = "I couldn't find this in your documents.";

function sourceLabel(source) {
  return source.page ? `${source.fileName}, page ${source.page}` : source.fileName;
}

function AskPanel() {
  const [question, setQuestion] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!question.trim() || loading) return;
    setLoading(true);
    setError("");
    setResult(null);
    try {
      setResult(await askQuestion(question.trim()));
    } catch (problem) {
      // 503 and validation errors arrive as problem details; fall back to a generic message.
      setError(problem?.detail || problem?.error || "Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const notFound = result && result.answer === NOT_FOUND;

  return (
    <div className="panel ask-panel">
      <h3>Ask your documents</h3>
      <p className="hint">Answers come only from documents you can access, with numbered citations.</p>
      <form onSubmit={handleSubmit}>
        <textarea
          rows={3}
          maxLength={1000}
          placeholder="e.g. Services keep throwing OutOfMemoryError after a deploy. What should I check?"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
        />
        <div className="ask-actions">
          <small>{question.length}/1000</small>
          <button type="submit" disabled={loading || !question.trim()}>
            {loading ? "Thinking…" : "Ask"}
          </button>
        </div>
      </form>

      {error && <div className="error-box">{error}</div>}

      {result && (
        <div className="ask-result">
          <div className={notFound ? "ask-answer ask-answer-empty" : "ask-answer"}>{result.answer}</div>
          {result.sources.length > 0 && (
            <>
              <h4>Sources</h4>
              <ol className="ask-sources">
                {result.sources.map((source, i) => (
                  <li key={`${source.fileId}-${source.chunkIndex}-${i}`}>
                    <strong>{sourceLabel(source)}</strong>
                    <span className="ask-score">score {source.score.toFixed(2)}</span>
                    <p>{source.snippet}</p>
                  </li>
                ))}
              </ol>
            </>
          )}
          <small className="hint">Answered in {(result.latencyMs / 1000).toFixed(1)} s</small>
        </div>
      )}
    </div>
  );
}

export default AskPanel;
