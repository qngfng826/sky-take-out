# 苍穹外卖后端镜像（Spring Boot 2.7.3 / Java 8）
# 构建前提：先在宿主机执行 mvn clean package "-Dmaven.test.skip=true"，
# 产出 sky-server/target/sky-server-1.0-SNAPSHOT.jar（详见 doc/部署说明.md）
FROM eclipse-temurin:8-jre

WORKDIR /app

COPY sky-server/target/sky-server-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
