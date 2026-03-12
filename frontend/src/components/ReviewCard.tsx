import type { ReviewCardProps } from "../types/review";

const badgeColor = (score: number): string => {
  if (score > 0.9) {
    return "#0f766e";
  }
  if (score >= 0.7) {
    return "#ca8a04";
  }
  return "#b91c1c";
};

export function ReviewCard({
  taskId,
  generatedContent,
  confidenceScore,
  reasoningTrace,
  status,
  createdAt,
  onApprove,
  onReject,
  busy = false
}: ReviewCardProps) {
  const borderStyle = confidenceScore < 0.8 ? "2px solid #b91c1c" : "1px solid #d7d2c4";

  return (
    <article className="review-card" style={{ border: borderStyle }}>
      <header>
        <strong title={taskId}>Task {taskId.slice(0, 8)}</strong>
        <span
          className="confidence-badge"
          style={{
            background: badgeColor(confidenceScore)
          }}
        >
          confidence {confidenceScore.toFixed(2)}
        </span>
      </header>

      <p className="review-meta">
        {status} · {new Date(createdAt).toLocaleString()}
      </p>

      <p className="review-content">{generatedContent}</p>
      <pre className="review-trace">{reasoningTrace}</pre>

      <div className="review-actions">
        <button type="button" className="button button-primary" onClick={onApprove} disabled={busy}>
          Approve
        </button>
        <button type="button" className="button button-danger" onClick={onReject} disabled={busy}>
          Reject
        </button>
      </div>
    </article>
  );
}
