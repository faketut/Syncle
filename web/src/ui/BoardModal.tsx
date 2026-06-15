import { useEffect, useState } from "react";
import { X } from "lucide-react";

export interface PullRequest {
  number: number;
  title: string;
  user: string;
  url: string;
  draft: boolean;
  createdAt: string;
}

interface FetchState {
  status: "idle" | "loading" | "ok" | "error";
  prs: PullRequest[];
  error?: string;
}

/** Modal opened by pressing F next to a `board` MapObject. Fetches the
 *  10 most recently updated open PRs from the configured GitHub repo via
 *  the unauthenticated REST API. Per the design system gate: never block
 *  the rest of the UI on third-party fetch, always render gracefully when
 *  the repo is missing / API rate-limited / network down. */
export function BoardModal({
  title,
  repo,
  onClose,
}: {
  title?: string;
  repo?: string;
  onClose: () => void;
}) {
  const [state, setState] = useState<FetchState>({
    status: repo ? "loading" : "idle",
    prs: [],
  });

  useEffect(() => {
    if (!repo) {
      setState({ status: "idle", prs: [] });
      return;
    }
    const ctl = new AbortController();
    setState({ status: "loading", prs: [] });
    const url = `https://api.github.com/repos/${encodeURIComponent(repo)}/pulls?state=open&per_page=10&sort=updated&direction=desc`;
    fetch(url, {
      signal: ctl.signal,
      headers: { Accept: "application/vnd.github+json" },
    })
      .then(async (res) => {
        if (!res.ok) {
          throw new Error(
            res.status === 404
              ? `Repo "${repo}" not found`
              : res.status === 403
                ? "Rate-limited by GitHub (60 req/hour without auth)"
                : `GitHub responded ${res.status}`,
          );
        }
        return (await res.json()) as Array<{
          number: number;
          title: string;
          html_url: string;
          user?: { login?: string } | null;
          draft?: boolean;
          created_at: string;
        }>;
      })
      .then((rows) => {
        const prs: PullRequest[] = rows.map((r) => ({
          number: r.number,
          title: r.title,
          user: r.user?.login ?? "?",
          url: r.html_url,
          draft: r.draft ?? false,
          createdAt: r.created_at,
        }));
        setState({ status: "ok", prs });
      })
      .catch((err: unknown) => {
        if (err instanceof DOMException && err.name === "AbortError") return;
        const msg = err instanceof Error ? err.message : String(err);
        setState({ status: "error", prs: [], error: msg });
      });
    return () => ctl.abort();
  }, [repo]);

  return (
    <div className="note-modal-backdrop" onClick={onClose}>
      <div
        className="note-modal board-modal"
        role="dialog"
        aria-modal="true"
        aria-label="GitHub PR board"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="note-modal-header">
          <span>
            <GitHubIcon /> {title && title.length > 0 ? title : "PR board"}
            {repo && <span className="board-repo"> · {repo}</span>}
          </span>
          <button onClick={onClose} aria-label="Close board" className="icon-btn"><X size={14} aria-hidden="true" /></button>
        </div>
        <div className="note-modal-body board-modal-body">
          {state.status === "idle" && (
            <div className="board-empty">
              <p>No repo configured for this board.</p>
              <p className="hint">
                Open the map editor, select the board, and set its repo
                (<code>owner/name</code>) in the inspector.
              </p>
            </div>
          )}
          {state.status === "loading" && (
            <div className="board-empty" aria-live="polite">
              Loading recent PRs from <code>{repo}</code>…
            </div>
          )}
          {state.status === "error" && (
            <div className="board-empty" role="alert">
              <p>Couldn't fetch PRs: {state.error}</p>
              {repo && (
                <p className="hint">
                  Try again later, or open{" "}
                  <a
                    href={`https://github.com/${repo}/pulls`}
                    target="_blank"
                    rel="noreferrer noopener"
                  >
                    {repo} on GitHub
                  </a>
                  .
                </p>
              )}
            </div>
          )}
          {state.status === "ok" && state.prs.length === 0 && (
            <div className="board-empty">No open PRs. Inbox zero!</div>
          )}
          {state.status === "ok" && state.prs.length > 0 && (
            <ul className="board-pr-list">
              {state.prs.map((pr) => (
                <li key={pr.number} className="board-pr-row">
                  <a
                    href={pr.url}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="board-pr-link"
                  >
                    <span className="board-pr-num">#{pr.number}</span>
                    <span className="board-pr-title">{pr.title}</span>
                  </a>
                  <span className="board-pr-meta">
                    {pr.draft && <span className="board-pr-draft">draft</span>}
                    <span>by {pr.user}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="note-modal-footer">
          <span className="key">Esc</span> to close
        </div>
      </div>
    </div>
  );
}

function GitHubIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      style={{ verticalAlign: "-3px", marginRight: 6 }}
    >
      <path d="M12 .5C5.6.5.5 5.6.5 12c0 5.1 3.3 9.4 7.9 10.9.6.1.8-.3.8-.6v-2c-3.2.7-3.9-1.5-3.9-1.5-.5-1.3-1.3-1.7-1.3-1.7-1.1-.7.1-.7.1-.7 1.2.1 1.8 1.2 1.8 1.2 1 1.8 2.8 1.3 3.5 1 .1-.8.4-1.3.7-1.6-2.6-.3-5.3-1.3-5.3-5.8 0-1.3.5-2.3 1.2-3.2-.1-.3-.5-1.5.1-3.2 0 0 1-.3 3.2 1.2.9-.3 1.9-.4 2.9-.4s2 .1 2.9.4c2.2-1.5 3.2-1.2 3.2-1.2.6 1.7.2 2.9.1 3.2.7.9 1.2 2 1.2 3.2 0 4.5-2.7 5.5-5.3 5.8.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6 4.6-1.5 7.9-5.8 7.9-10.9C23.5 5.6 18.4.5 12 .5z" />
    </svg>
  );
}
