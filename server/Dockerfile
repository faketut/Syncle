FROM node:20-alpine AS build
WORKDIR /app
# Inject any extra root CAs (e.g. a corporate TLS proxy like Zscaler) BEFORE
# the first network call. We append directly to /etc/ssl/cert.pem (musl/Alpine
# default bundle) instead of using `apk add ca-certificates`, because apk
# itself needs working TLS to install anything. The glob lets builds succeed
# for contributors with no extra CAs in certs/.
COPY certs/ /tmp/extra-ca/
RUN if ls /tmp/extra-ca/*.crt >/dev/null 2>&1; then \
      cat /tmp/extra-ca/*.crt >> /etc/ssl/cert.pem; \
    fi && rm -rf /tmp/extra-ca
# Node ships its own CA bundle (not /etc/ssl/cert.pem), so npm needs this hint
# to trust the proxy CA when contacting registry.npmjs.org.
ENV NODE_EXTRA_CA_CERTS=/etc/ssl/cert.pem
RUN apk add --no-cache python3 make g++
COPY package.json ./
RUN npm install --no-audit --no-fund
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY certs/ /tmp/extra-ca/
RUN if ls /tmp/extra-ca/*.crt >/dev/null 2>&1; then \
      cat /tmp/extra-ca/*.crt >> /etc/ssl/cert.pem; \
    fi && rm -rf /tmp/extra-ca
ENV NODE_EXTRA_CA_CERTS=/etc/ssl/cert.pem
RUN apk add --no-cache tini
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund
COPY --from=build /app/dist ./dist
ENV NODE_ENV=production
EXPOSE 8787
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "dist/server.js"]
