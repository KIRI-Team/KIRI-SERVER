#!/bin/bash
set -euxo pipefail

# swap
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
fi

# Docker
dnf install -y docker

systemctl enable --now docker
usermod -aG docker ec2-user

# Docker Compose 플러그인
COMPOSE_VERSION="v5.6.0"
COMPOSE_SHA256="733ec76717ceb59052a9609b9dadfb523b2df8eab57a54212872d10a58078ea2"
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-aarch64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
echo "${COMPOSE_SHA256}  /usr/local/lib/docker/cli-plugins/docker-compose" | sha256sum -c -
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 인증서 자동 갱신
cat > /etc/systemd/system/certbot-renew.service << 'EOF'
[Unit]
Description=Renew Let's Encrypt certificates (certbot container)
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
WorkingDirectory=/opt/kiri/nginx
ExecStart=/usr/bin/docker compose run --rm certbot renew --webroot -w /var/www/certbot --quiet
ExecStartPost=/usr/bin/docker exec kiri-nginx nginx -s reload
EOF

cat > /etc/systemd/system/certbot-renew.timer << 'EOF'
[Unit]
Description=Run certbot renew twice a day

[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=1h
Persistent=true

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now certbot-renew.timer

#psql
dnf install -y postgresql16 || true