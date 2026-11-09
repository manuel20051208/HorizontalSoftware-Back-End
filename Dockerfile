# syntax=docker/dockerfile:1

# =============================================================================
# Dockerfile multistage para HorizontalSoftware (Spring Boot 4 / Java 25)
#
# Parte del .jar YA COMPILADO. Antes de construir la imagen genera el artefacto:
#     ./mvnw clean package            (o -DskipTests si no quieres correr pruebas)
#
# La imagen final solo lleva el JRE: ni JDK ni Maven/SDK viajan al contenedor.
# =============================================================================


# -----------------------------------------------------------------------------
# Etapa 1: tomar el .jar ya construido y extraer sus capas (layered jar).
# Se usa el JRE solo como herramienta para ejecutar el "jarmode tools".
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS builder
WORKDIR /builder

# Ruta al fat-jar ya empaquetado (sobreescribible con --build-arg JAR_FILE=...)
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar

# Divide el jar en capas (dependencias, loader, snapshots, aplicacion)
# para que Docker cachee lo que no cambia entre builds.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted


# -----------------------------------------------------------------------------
# Etapa 2: imagen final de ejecucion. Solo JRE + las capas de la app.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /application

# Usuario sin privilegios (buena practica: no correr como root)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiamos capa por capa (de menos a mas volatil) para mejor cache
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# Puerto por defecto de Spring Boot
EXPOSE 8080

# Opciones de JVM configurables sin reconstruir la imagen
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]
