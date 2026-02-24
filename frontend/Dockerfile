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

# Remove unnecessary packages to minimize attack surface
RUN apk del --purge apk-tools && \
    rm -rf /var/cache/apk/* /tmp/* /var/tmp/*

# Copy built artifacts from builder
COPY --from=builder /build/dist/dashboard/browser /usr/share/nginx/html

# Copy optimized nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Create nginx pid directory (writable by nginx user)
RUN mkdir -p /var/run/nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/run/nginx /var/cache/nginx /var/log/nginx /usr/share/nginx/html

# Run as non-root user
USER nginx

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:80/index.html || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
