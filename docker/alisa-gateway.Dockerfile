FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY ./packages/alisa-gateway-*.jar /app/alisa-gateway.jar
COPY ./examples/gateway/nacos-routes.yaml /app/nacos-routes.yaml
EXPOSE 8100
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "/app/alisa-gateway.jar"]
