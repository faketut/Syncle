# Extra trusted root CAs (gitignored)

If your network uses a TLS-intercepting proxy (Zscaler, Netskope, Palo Alto,
corporate firewall, etc.), Alpine inside the Docker build won't trust the
proxy's leaf certs and `apk` / `npm install` will fail with
`TLS: server certificate not trusted`.

Drop the proxy's root CA(s) into this folder as `*.crt` (PEM format) and
re-run `docker compose up --build`. The Dockerfile appends them to
`/etc/ssl/cert.pem` before any network call.

## Quick export (macOS + Zscaler example)

```bash
security find-certificate -a -c "Zscaler" -p \
  /Library/Keychains/System.keychain > certs/zscaler-root.crt
```

Files in this directory are git-ignored by default (see `.gitignore`).
