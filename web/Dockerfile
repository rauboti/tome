# syntax=docker/dockerfile:1

# ---- Build stage: install deps + produce the static Vite build ----
FROM node:22-alpine AS build
WORKDIR /web
RUN corepack enable

# Registry auth for the private @rauboti scope. The token is supplied as a
# BuildKit secret (never written into an image layer) and substituted into the
# yarnrc template exactly as CI does. After install we restore the *placeholder*
# template over the real-token file: Yarn still needs its `nodeLinker:
# node-modules` setting at build time (without a yarnrc it falls back to PnP and
# ignores node_modules), but the real token must not persist in the layer. Both
# steps run in one RUN, so the token version is never committed.
COPY .yarnrc.yml.example package.json yarn.lock ./
RUN --mount=type=secret,id=rauboti_token \
    sed "s|<your-token>|$(cat /run/secrets/rauboti_token)|" .yarnrc.yml.example > .yarnrc.yml \
 && yarn install --immutable \
 && cp .yarnrc.yml.example .yarnrc.yml

# Sources, then the production build. A production build serves same-origin, so
# the client calls /api, which nginx proxies to the api service (see nginx.conf).
COPY . .
RUN yarn build

# ---- Runtime stage: nginx serving the static build ----
FROM nginx:alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 80
