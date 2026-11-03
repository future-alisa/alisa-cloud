FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY ./packages/alisa-user-*.jar /app/alisa-user.jar
EXPOSE 8084
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "/app/alisa-user.jar"]
