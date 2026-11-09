set -eu
cd /workspace/source-web/alisa-admin-web
corepack enable
corepack prepare pnpm@10.18.1 --activate
pnpm config set https-proxy "$HTTPS_PROXY"
pnpm config set proxy "$HTTP_PROXY"
pnpm install --frozen-lockfile --store-dir /workspace/pnpm
ALISA_ADMIN_BASE=/admin/ pnpm build
mkdir -p /workspace/runtime-web/html
cp -R dist/. /workspace/runtime-web/html/
cp deploy/nginx.conf /workspace/runtime-web/nginx.conf
cp ../deploy/admin/build/Dockerfile.web /workspace/runtime-web/Dockerfile
