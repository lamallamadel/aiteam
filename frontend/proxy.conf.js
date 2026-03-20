const PROXY_CONFIG = {
  "/api": {
    target: "http://ai-orchestrator:8080",
    secure: false,
    changeOrigin: true
  },

  "/actuator": {
    target: "http://ai-orchestrator:8080",
    secure: false,
    changeOrigin: true
  },
  "/v3/api-docs": {
    target: "http://ai-orchestrator:8080",
    secure: false,
    changeOrigin: true
  },
  "/swagger-ui": {
    target: "http://ai-orchestrator:8080",
    secure: false,
    changeOrigin: true
  },
  "/.well-known": {
    target: "http://ai-orchestrator:8080",
    secure: false,
    changeOrigin: true
  }
};

module.exports = PROXY_CONFIG;
