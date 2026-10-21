FROM node:24-bookworm-slim AS deps
WORKDIR /workspace
RUN npm install --global pnpm@10
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json
COPY packages/api-client/package.json packages/api-client/package.json
RUN pnpm install --frozen-lockfile

FROM deps AS build
WORKDIR /workspace
COPY apps/web apps/web
COPY packages/api-client packages/api-client
COPY packages/config packages/config
RUN pnpm --filter @covie/web build

FROM gcr.io/distroless/nodejs24-debian13:nonroot@sha256:c1326ce1aad4b976dce3a3da8b7eb5c8328e635e312309a7a0e5319d1470be0d AS runner
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0
COPY --from=build --chown=10001:10001 /workspace/apps/web/.next/standalone ./
COPY --from=build --chown=10001:10001 /workspace/apps/web/.next/static ./apps/web/.next/static
COPY --from=build --chown=10001:10001 /workspace/apps/web/public ./apps/web/public
USER 10001:10001
EXPOSE 3000
CMD ["apps/web/server.js"]
