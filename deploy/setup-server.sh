#!/usr/bin/env bash
# Ubuntu 24.04 서버 첫 준비: Docker Engine + compose 플러그인 설치, 저장소 내려받기. 서버에서 한 번 실행한다.
set -euo pipefail
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
[ -d ~/blog ] || git clone https://github.com/AIPKANG/docs.git ~/blog
echo "완료: 다시 접속한 뒤 ~/blog/.env를 채우고 deploy/first-deploy.sh를 실행하세요."
