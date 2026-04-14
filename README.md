# ApHinity-Java-AWS
# Description
AWS backend for forward-facing Java server for the ApHinity Technologies partner and client analytics portal.

# Deployment Notes
- Frontend assets are compiled during Docker image build and copied into the runtime image as static files.
- `TURNSTILE_SITE_KEY` is passed into the frontend build as `VITE_TURNSTILE_SITE_KEY` via `docker-compose.yml` build args, so key changes require an image rebuild.
- If you change frontend code or Docker build context files, rebuild and recreate the container:
  - `docker compose build --no-cache app && docker compose up -d --force-recreate app`
- If a stale bundle is still observed after deploy, purge CDN/browser cache for `index.html` and old `/assets/*` chunks. For testing, you can put Cloudflare into development mode to bypass CDN.
- Server restarts every morning at 01:00 MST. Specific reaper threads run at 00:00 MST.
- Server restart job completely deletes all dangling images.
- Updating the AWS instance will reset the buildx update thereby breaking docker-compose. There is a script in /home/wapp/ to run to rectify this.

# Contact
AWS instance is owned by AWS account ID 533266982969. Contact Trenton Mosher at 480-823-1354 or tmosher@aphinitytech.com for inquiry into SSH keys for access and development.
