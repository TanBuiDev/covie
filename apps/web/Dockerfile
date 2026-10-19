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

FROM node:24-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0
# The production server invokes Node directly; npm/npx are build-time tools and
# their bundled dependency tree need not be present in the runtime image.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx
COPY --from=build --chown=10001:10001 /workspace/apps/web/.next/standalone ./
COPY --from=build --chown=10001:10001 /workspace/apps/web/.next/static ./apps/web/.next/static
COPY --from=build --chown=10001:10001 /workspace/apps/web/public ./apps/web/public
USER 10001:10001
EXPOSE 3000
CMD ["node","apps/web/server.js"]
