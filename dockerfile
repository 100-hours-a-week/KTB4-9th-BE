# 1단계: 빌드 및 레이어 추출
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# 캐싱용 설정 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# 전체 소스 복사 및 빌드
COPY src src
RUN ./gradlew clean bootJar -x test --no-daemon

# --layers 와 --launcher 옵션을 모두 주어 4대 레이어와 JarLauncher를 추출
RUN java -Djarmode=tools -jar build/libs/*-SNAPSHOT.jar extract --layers --launcher --destination extracted


# 2단계: 레이어별 복사 및 실행
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# 변경이 드문 의존성부터 순서대로 레이어 복사 (도커 캐싱 극대화)
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

EXPOSE 8080

# 최신 스프링 부트 런처 실행
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]