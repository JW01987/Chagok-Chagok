# BT-06 | CD 파이프라인 구축 (개발 서버)

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 0 — 환경 세팅
- **상태**: ⬜ 대기
- **선행 태스크**: BT-05
- **완료 기준**: develop 머지 → 자동 배포 → EC2 Health Check 200 응답

---

## 개요

develop 브랜치에 머지될 때마다 AWS EC2 개발 서버에 자동 배포되는 CD 파이프라인을 구축한다.
Docker 이미지를 ECR에 푸시하고 EC2에서 컨테이너를 재시작하는 방식.

---

## BT-06-01 | AWS EC2 인스턴스 생성

**작업 유형**: 수동 (AWS 콘솔)

### EC2 설정

```
AMI: Ubuntu Server 22.04 LTS
Instance type: t3.small (개발 서버)
Key pair: chagok-dev-key (새로 생성 → .pem 파일 로컬 보관)
Network:
  - VPC: 기본 VPC
  - Subnet: 퍼블릭 서브넷
  - Auto-assign public IP: 활성화
Storage: 20GB gp3
```

### 보안 그룹 설정

| 유형 | 프로토콜 | 포트 범위 | 소스 | 설명 |
|---|---|---|---|---|
| SSH | TCP | 22 | 내 IP | 관리용 |
| HTTP | TCP | 80 | 0.0.0.0/0 | HTTP (HTTPS 리다이렉트) |
| HTTPS | TCP | 443 | 0.0.0.0/0 | 운영 트래픽 |
| 사용자 정의 | TCP | 8080 | 0.0.0.0/0 | Spring Boot (초기 테스트용, 이후 제거) |

### 생성 후 처리

```bash
# PEM 키 권한 설정
chmod 400 chagok-dev-key.pem

# EC2 접속 확인
ssh -i chagok-dev-key.pem ubuntu@{EC2_PUBLIC_IP}

# GitHub Secrets에 등록
# EC2_HOST: EC2 퍼블릭 IP
# EC2_SSH_KEY: PEM 파일 내용 전체 (cat chagok-dev-key.pem)
```

### 완료 확인
- [ ] EC2 인스턴스 Running 상태
- [ ] SSH 접속 성공

---

## BT-06-02 | EC2 서버 기본 구성

**작업 유형**: 명령어 실행 (SSH로 EC2에서)

### EC2 접속 후 실행

```bash
# 시스템 업데이트
sudo apt-get update && sudo apt-get upgrade -y

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
newgrp docker

# Docker Compose 설치
sudo apt-get install -y docker-compose-plugin

# Nginx 설치
sudo apt-get install -y nginx

# AWS CLI 설치
sudo apt-get install -y awscli

# Docker 설치 확인
docker --version
docker compose version
```

### Nginx 설정

```bash
# Nginx 설정 파일 작성
sudo tee /etc/nginx/sites-available/chagok << 'EOF'
server {
    listen 80;
    server_name _;  # 개발 서버는 IP 직접 접근 허용

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90;
    }

    location /health {
        proxy_pass http://localhost:8080/health;
        access_log off;
    }
}
EOF

# 기본 사이트 비활성화 + chagok 활성화
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -s /etc/nginx/sites-available/chagok /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl restart nginx
sudo systemctl enable nginx
```

### 배포용 디렉토리 생성

```bash
# 배포 스크립트 디렉토리
mkdir -p /home/ubuntu/chagok
mkdir -p /home/ubuntu/chagok/logs
```

### 완료 확인
- [ ] `docker --version` 출력 확인
- [ ] `sudo systemctl status nginx` Active 상태
- [ ] `curl http://localhost:80` → 502 (백엔드 미실행) 또는 연결 확인

---

## BT-06-03 | AWS ECR 저장소 생성

**작업 유형**: 수동 (AWS 콘솔) + 명령어

### AWS 콘솔에서 ECR 생성

```
AWS 콘솔 → ECR → Create repository
Repository name: chagok-backend
Visibility: Private
Image tag mutability: Mutable
Scan on push: 활성화
```

### IAM 설정 (GitHub Actions용)

```
AWS IAM → 사용자 생성: github-actions-deploy
권한 정책 연결:
  - AmazonEC2ContainerRegistryFullAccess
  - (EC2 배포용은 EC2 IAM Role 방식 사용 — 아래 참고)
Access Key 생성 후 GitHub Secrets에 등록:
  - AWS_ACCESS_KEY_ID
  - AWS_SECRET_ACCESS_KEY
  - ECR_REGISTRY: {계정ID}.dkr.ecr.ap-northeast-2.amazonaws.com
  - ECR_REPOSITORY: chagok-backend
```

### 완료 확인
- [ ] ECR 저장소 URL 확인
- [ ] GitHub Secrets에 ECR 관련 값 등록

---

## BT-06-04 | CD 워크플로 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `.github/workflows/cd-dev.yml`

```yaml
name: CD — Development

on:
  push:
    branches: [ develop ]

env:
  AWS_REGION: ap-northeast-2
  ECR_REPOSITORY: chagok-backend

jobs:
  deploy:
    name: Deploy to Dev Server
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Build JAR (테스트 제외)
        working-directory: backend
        run: |
          chmod +x gradlew
          ./gradlew bootJar --no-daemon -x test

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build, tag, and push image to ECR
        id: build-image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./backend
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:latest ./backend
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest
          echo "image=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT

      - name: Deploy to EC2
        uses: appleboy/ssh-action@v1.0.3
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
          DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
          JWT_SECRET: ${{ secrets.JWT_SECRET }}
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          envs: ECR_REGISTRY,ECR_REPOSITORY,IMAGE_TAG,DB_PASSWORD,JWT_SECRET
          script: |
            # ECR 로그인
            aws ecr get-login-password --region ap-northeast-2 | \
              docker login --username AWS --password-stdin $ECR_REGISTRY

            # 최신 이미지 pull
            docker pull $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

            # 기존 컨테이너 종료
            docker stop chagok-backend || true
            docker rm chagok-backend || true

            # 새 컨테이너 실행
            docker run -d \
              --name chagok-backend \
              --restart unless-stopped \
              -p 8080:8080 \
              -e SPRING_PROFILES_ACTIVE=dev \
              -e DB_HOST=${{ secrets.DB_HOST }} \
              -e DB_PORT=5432 \
              -e DB_NAME=chagok_db \
              -e DB_USERNAME=${{ secrets.DB_USERNAME }} \
              -e DB_PASSWORD=$DB_PASSWORD \
              -e REDIS_HOST=${{ secrets.REDIS_HOST }} \
              -e REDIS_PORT=6379 \
              -e JWT_SECRET=$JWT_SECRET \
              $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

            # Health Check 대기 (최대 60초)
            for i in $(seq 1 12); do
              if curl -sf http://localhost:8080/health; then
                echo "✅ Health check passed"
                break
              fi
              echo "⏳ Waiting... ($i/12)"
              sleep 5
            done

            # 오래된 이미지 정리
            docker image prune -f

      - name: Notify deployment result
        if: always()
        run: |
          if [ "${{ job.status }}" == "success" ]; then
            echo "✅ 개발 서버 배포 완료: ${{ github.sha }}"
          else
            echo "❌ 개발 서버 배포 실패"
          fi
```

### 완료 확인
- [ ] 파일 생성 후 develop에 푸시
- [ ] GitHub Actions 탭에서 워크플로 실행 확인

---

## BT-06-05 | CD 동작 검증

**작업 유형**: 검증

### 검증 절차

```bash
# 1. feature 브랜치에서 작업 후 develop으로 PR
git checkout -b feature/test-cd
echo "# CD Test" >> README.md
git add README.md
git commit -m "test: CD 파이프라인 동작 확인"
git push origin feature/test-cd

# 2. GitHub에서 develop으로 PR 생성 → CI 통과 → 머지

# 3. GitHub Actions에서 cd-dev.yml 실행 확인
```

### 최종 확인

```bash
# EC2 Public IP로 Health Check
curl http://{EC2_PUBLIC_IP}/health
# 예상: {"status":"UP","timestamp":"...","service":"chagok-backend"}

# Swagger 접근 (개발 환경은 활성화)
open http://{EC2_PUBLIC_IP}/swagger-ui.html
```

### 완료 체크리스트
- [ ] develop 머지 → GitHub Actions cd-dev.yml 자동 트리거
- [ ] ECR에 이미지 push 성공
- [ ] EC2에서 컨테이너 재시작 성공
- [ ] `curl http://{EC2_PUBLIC_IP}/health` → 200 OK

---

## 완료 체크리스트

- [ ] BT-06-01: EC2 인스턴스 생성 + SSH 접속 확인
- [ ] BT-06-02: Docker + Nginx 설치 + 서버 기본 구성
- [ ] BT-06-03: ECR 저장소 생성 + GitHub Secrets 등록
- [ ] BT-06-04: `cd-dev.yml` 작성 완료
- [ ] BT-06-05: develop 머지 → 자동 배포 → Health Check 성공

**Phase 0 완료! → Phase 1 BT-07 (DB 마이그레이션)으로 이동**

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
