# Etapa 1: Compilación
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compilamos saltando los tests para que sea más rápido
RUN mvn clean package -DskipTests

# Etapa 2: Imagen final para producción
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copiamos el .jar compilado de la etapa anterior
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Topes de memoria para que la JVM no se expanda hasta el límite del contenedor
# (Railway cobra por RAM usada). Se puede pisar con la variable JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-Xmx384m -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
# Ejecutamos con el perfil "prod" para apagar el simulador
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]