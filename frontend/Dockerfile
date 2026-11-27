# syntax=docker/dockerfile:1
#
# The API and WebSocket addresses are compiled into the bundle by Vite, so they are BUILD args:
#   docker build -t testforge-frontend --build-arg VITE_API_URL=https://api.example.com frontend

# ---- 1. build the static site --------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=http://localhost:8080
ARG VITE_WS_URL=
ENV VITE_API_URL=$VITE_API_URL \
    VITE_WS_URL=$VITE_WS_URL
RUN npm run build

# ---- 2. serve it with nginx ----------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
