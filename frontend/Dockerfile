# Stage 1: Build
FROM node:22-alpine AS builder
WORKDIR /build

# Copy package files
COPY package*.json ./

# Install dependencies with cache mount
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# Copy source code
COPY . .

# Build Angular app
RUN npm run build -- --configuration production

# Stage 2: Serve
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# Copy built artifacts from builder
COPY --from=builder /build/dist/dashboard/browser .

# Copy optimized nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Remove default nginx config and clean up
RUN rm -f /etc/nginx/conf.d/default.conf.bak && \
    # Ensure nginx runs as non-root nginx user (already exists in nginx image)
    chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/run /var/log/nginx

# Switch to non-root user (nginx user already exists in the image)
USER nginx

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:80/index.html || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
