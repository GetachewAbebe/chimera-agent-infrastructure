import { type FormEvent, useState } from "react";

type CampaignComposerProps = {
  busy: boolean;
  onSubmit: (payload: {
    goal: string;
    workerId: string;
    requiredResources: string[];
  }) => Promise<void>;
};

const splitResources = (text: string): string[] =>
  text
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

export function CampaignComposer({ busy, onSubmit }: CampaignComposerProps) {
  const [goal, setGoal] = useState("");
  const [workerId, setWorkerId] = useState("worker-alpha");
  const [resources, setResources] = useState("news://ethiopia/fashion/trends");

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!goal.trim()) {
      return;
    }

    await onSubmit({
      goal: goal.trim(),
      workerId: workerId.trim() || "worker-alpha",
      requiredResources: splitResources(resources)
    });

    setGoal("");
  };

  return (
    <section className="panel composer-panel">
      <div className="section-header">
        <h2>Campaign Composer</h2>
        <p>Translate strategic intent into planner tasks.</p>
      </div>

      <form onSubmit={handleSubmit} className="composer-grid">
        <label>
          Goal
          <textarea
            value={goal}
            onChange={(event) => setGoal(event.target.value)}
            placeholder="Promote the new summer fashion line in Ethiopia"
            required
            rows={4}
          />
        </label>

        <label>
          Worker ID
          <input
            value={workerId}
            onChange={(event) => setWorkerId(event.target.value)}
            placeholder="worker-alpha"
          />
        </label>

        <label>
          Required Resources (one per line)
          <textarea
            value={resources}
            onChange={(event) => setResources(event.target.value)}
            rows={4}
            placeholder="news://ethiopia/fashion/trends"
          />
        </label>

        <button type="submit" className="button button-primary" disabled={busy}>
          {busy ? "Submitting..." : "Create Campaign"}
        </button>
      </form>
    </section>
  );
}
