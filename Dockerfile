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
# Ejecutamos con el perfil "prod" para apagar el simulador
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]