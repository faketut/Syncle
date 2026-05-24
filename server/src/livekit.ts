import { AccessToken } from "livekit-server-sdk";

export interface SignedToken {
  token: string;
  expiresAt: number;
}

export interface TokenSignerOptions {
  apiKey: string;
  apiSecret: string;
  ttlSeconds: number;
}

export class TokenSigner {
  constructor(private readonly opts: TokenSignerOptions) {}

  async sign(userId: string, room: string, name: string): Promise<SignedToken> {
    const at = new AccessToken(this.opts.apiKey, this.opts.apiSecret, {
      identity: userId,
      name,
      ttl: this.opts.ttlSeconds,
    });
    at.addGrant({
      roomJoin: true,
      room,
      canPublish: true,
      canSubscribe: true,
      canPublishData: true,
    });
    return {
      token: await at.toJwt(),
      expiresAt: Date.now() + this.opts.ttlSeconds * 1000,
    };
  }
}
