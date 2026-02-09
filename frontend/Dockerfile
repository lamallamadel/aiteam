# Stage 1: Serve with Nginx
FROM nginx:alpine
# Copy the locally built artifacts directly from the host
COPY dist/dashboard/browser /usr/share/nginx/html
# Copy custom nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
