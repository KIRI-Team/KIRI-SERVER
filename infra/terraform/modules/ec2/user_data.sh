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

# Docker, Nginx
dnf install -y docker nginx python3

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

systemctl enable --now nginx

# certbot
python3 -m venv /opt/certbot
/opt/certbot/bin/pip install --upgrade pip
/opt/certbot/bin/pip install certbot certbot-nginx
ln -sf /opt/certbot/bin/certbot /usr/bin/certbot

#psql
dnf install -y postgresql16 || true