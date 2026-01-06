FROM openjdk:17-ea-17-jdk-slim
WORKDIR /app
COPY ./target/package/alisa-gateway-*.jar /app/alisa-gateway.jar
EXPOSE 8100
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "/app/alisa-gateway.jar"]
