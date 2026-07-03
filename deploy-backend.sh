#!/bin/bash

echo "🚀 백엔드 API 빌드 및 배포 스크립트를 시작합니다..."

# 1. Spring Boot Jar 빌드
echo "📦 1. Spring Boot(agentic-api) bootJar 빌드 중..."
./gradlew :agentic-api:bootJar
if [ $? -ne 0 ]; then
    echo "❌ 빌드 실패! 스크립트를 종료합니다."
    exit 1
fi
echo "✅ 빌드 성공!"

# 2. Docker 이미지 빌드
echo "🐳 2. Docker 이미지 빌드 중 (parkjihyeon/agentic-backend:latest)..."
docker build -t parkjihyeon/agentic-backend:latest -f agentic-api/Dockerfile agentic-api
if [ $? -ne 0 ]; then
    echo "❌ 도커 이미지 빌드 실패! 도커가 켜져 있는지 확인해주세요."
    exit 1
fi
echo "✅ 도커 이미지 빌드 성공!"

# 3. Docker 이미지 푸시
echo "☁️  3. Docker Hub로 이미지 푸시 중..."
docker push parkjihyeon/agentic-backend:latest
if [ $? -ne 0 ]; then
    echo "❌ 도커 푸시 실패! 'docker login' 상태를 확인해주세요."
    exit 1
fi
echo "✅ 도커 푸시 성공!"

# 4. 새 이미지로 컨테이너 갱신 (선택적)
echo "🔄 4. docker-compose 컨테이너 업데이트 중..."
docker-compose pull backend
docker-compose up -d
echo "🎉 모든 배포 작업이 완료되었습니다! (서버 재시작 완료)"
