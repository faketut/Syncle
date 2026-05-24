import { describe, it, expect } from "vitest";
import { TokenSigner } from "../src/livekit.js";
import { verifyJoinToken } from "../src/auth.js";

describe("TokenSigner", () => {
  const signer = new TokenSigner({
    apiKey: "devkey",
    apiSecret: "secret-secret-secret-secret",
    ttlSeconds: 60,
  });

  it("produces JWT verifiable with same secret containing identity + room grant", async () => {
    const { token, expiresAt } = await signer.sign("user-1", "room-A", "Alice");
    expect(token.split(".")).toHaveLength(3);
    expect(expiresAt).toBeGreaterThan(Date.now());

    const payload = await verifyJoinToken(token, "secret-secret-secret-secret");
    expect(payload.sub).toBe("user-1");
    expect(payload.video.room).toBe("room-A");
    expect(payload.video.roomJoin).toBe(true);
  });

  it("rejects token verified with wrong secret", async () => {
    const { token } = await signer.sign("user-1", "room-A", "Alice");
    await expect(verifyJoinToken(token, "wrong-secret-wrong-secret")).rejects.toThrow();
  });
});
