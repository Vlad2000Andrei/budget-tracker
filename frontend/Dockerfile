# Stage 1: Build the static React application
FROM node:20-alpine AS build
WORKDIR /app

# Copy package descriptors and cache node modules
COPY package*.json ./
RUN npm ci

# Copy the rest of the application files and build
COPY . .
RUN npm run build

# Stage 2: Serve static files with Nginx
FROM nginx:alpine

# Copy build artifacts to Nginx server html directory
COPY --from=build /app/dist /usr/share/nginx/html

# Copy Nginx template and entrypoint script
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

# Sanitize potential CRLF line endings from Windows edit environments and mark executable
RUN sed -i 's/\r$//' /usr/local/bin/docker-entrypoint.sh && \
    chmod +x /usr/local/bin/docker-entrypoint.sh

# Expose port (default 80 inside container)
EXPOSE 80

# Configure entrypoint and standard runtime CMD
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
