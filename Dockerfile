# 构建阶段：容器内直接编译，不依赖本地 target 目录
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY frontend ./frontend
RUN mvn -q -DskipTests package

# 运行阶段：只保留 JRE 和 jar，镜像更小
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/chinese-chess-1.0.0.jar app.jar
ENV APP_ARCHIVE_DIR=/app/data/games
RUN mkdir -p /app/data/games
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
