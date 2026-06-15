import { LOCAL_CHAT_IDENTITY, useSyncle } from "../state/syncleStore";
import { statusMeta } from "../domain/avatarStatus";

/** Compact floating panel shown when mini-mode is on. Lists the local user
 *  and every peer currently at the same table (or all peers when not
 *  seated), with a live speaking indicator. Spatial canvas + chat are
 *  hidden by SyncleScreen while this is mounted. */
export function MiniPanel({ onExit }: { onExit: () => void }) {
  const self = useSyncle((s) => s.self);
  const speakingIdentities = useSyncle((s) => s.speakingIdentities);
  // Re-subscribe to peer-table signature so the roster only updates when
  // someone sits/stands, not every position packet.
  const peerSig = useSyncle((s) => {
    let sig = "";
    for (const [k, v] of s.peers) sig += `${k}:${v.tableId ?? ""};`;
    return sig;
  });
  void peerSig;

  if (!self) return null;
  const peers = Array.from(useSyncle.getState().peers.values());
  const visible = self.tableId == null
    ? peers
    : peers.filter((p) => p.tableId === self.tableId);

  const rows = [
    {
      identity: LOCAL_CHAT_IDENTITY,
      name: `${self.nickname} (you)`,
      color: self.color,
      status: self.status,
      speaking: speakingIdentities.has(LOCAL_CHAT_IDENTITY),
    },
    ...visible.map((p) => ({
      identity: p.identity,
      name: p.name ?? p.identity.slice(0, 6),
      color: p.color ?? "#5AC8FA",
      status: p.status,
      speaking: speakingIdentities.has(p.identity),
    })),
  ];

  return (
    <aside className="mini-panel" aria-label="Mini mode">
      <header className="mini-panel-header">
        <span className="mini-panel-title">Mini mode</span>
        <button
          type="button"
          className="mini-panel-exit"
          onClick={onExit}
          title="Exit mini mode"
          aria-label="Exit mini mode"
        >
          <ExpandIcon />
        </button>
      </header>
      <div className="mini-panel-sub">
        {self.tableId
          ? `At table ${self.tableId} · ${rows.length - 1} other${rows.length - 1 === 1 ? "" : "s"}`
          : `Not seated · ${rows.length - 1} peer${rows.length - 1 === 1 ? "" : "s"} visible`}
      </div>
      <ul className="mini-panel-list">
        {rows.map((row) => {
          const meta = statusMeta(row.status);
          return (
            <li
              key={row.identity}
              className={`mini-panel-row${row.speaking ? " speaking" : ""}`}
            >
              <span
                className="mini-panel-dot"
                style={{ background: row.color }}
                aria-hidden="true"
              />
              <span className="mini-panel-name">{row.name}</span>
              <span
                className="mini-panel-status"
                style={{ color: meta.ringColor }}
                title={meta.label}
              >
                {meta.label}
              </span>
              {row.speaking && (
                <span className="mini-panel-speaking" aria-label="speaking">
                  <SpeakerIcon />
                </span>
              )}
            </li>
          );
        })}
      </ul>
    </aside>
  );
}

function ExpandIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <polyline points="15 3 21 3 21 9" />
      <polyline points="9 21 3 21 3 15" />
      <line x1="21" y1="3" x2="14" y2="10" />
      <line x1="3" y1="21" x2="10" y2="14" />
    </svg>
  );
}

function SpeakerIcon() {
  return (
    <svg
      width="12"
      height="12"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
      <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
    </svg>
  );
}
