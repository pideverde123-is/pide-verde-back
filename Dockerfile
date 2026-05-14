# Etapa 1: Construcción (Build)
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
# Le damos permisos al archivo mvnw para que no falle en Linux
RUN chmod +x mvnw
# Compilamos el proyecto
RUN ./mvnw clean package -DskipTests

# Etapa 2: Ejecución (Run)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copiamos el archivo .jar ya compilado de la etapa anterior
COPY --from=build /app/target/*.jar app.jar
# Exponemos el puerto de la API
EXPOSE 8080
# Encendemos el motor Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]