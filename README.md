# ApHinity-Java-AWS
# Description
AWS backend for forward-facing Java server for the ApHinity Technologies partner and client analytics portal.

# Deployment Notes
- Frontend assets are compiled at container startup (`npm run frontend:build`).
- In Docker, the frontend Turnstile key now derives from runtime `TURNSTILE_SITE_KEY` (mapped to `VITE_TURNSTILE_SITE_KEY` in `Dockerfile` entrypoint), so key changes require a container restart.
- If you change frontend code or Docker build context files, rebuild the image before restart:
  - `docker compose up --build -d`
- If a stale bundle is still observed after deploy, purge CDN/browser cache for `index.html` and old `/assets/*` chunks. For testing, you can put Cloudflare into development mode to bypass CDN.

# Contact
AWS instance is owned by AWS account ID 533266982969. Contact Trenton Mosher at 480-823-1354 or tmosher@aphinitytech.com for inquiry into SSH keys for access and development.