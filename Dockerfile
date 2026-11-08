# =========================================================
# Saphikuna - panel de administracion (Spring Boot) para Render
# Dos etapas: se compila con Maven y se ejecuta solo con el JRE (imagen final pequena).
# =========================================================

# ---------- 1) Compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Primero solo el pom: si no cambia, las dependencias quedan en cache entre despliegues
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package \
    && cp target/saphikuna-*.jar /app.jar

# ---------- 2) Ejecutar ----------
# Imagen basada en Ubuntu (no Alpine): la libreria nativa de WebP necesita glibc.
FROM eclipse-temurin:21-jre-jammy

# fontconfig/freetype: las clases graficas de Java (fotos y PDF) las cargan aunque no dibujen texto
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig libfreetype6 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 app

COPY --from=build /app.jar /app/app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod

# Memoria pensada para 512 MB en total (heap 192 MB + metaspace, hilos y bibliotecas nativas):
#  - SerialGC y C1 solamente: menos memoria y menos CPU (el plan gratis tiene muy poca)
#  - Heap*FreeRatio: devuelve memoria al sistema despues de una tarea pesada (PDF, fotos)
#  - ExitOnOutOfMemoryError: si algo llegara a agotar la memoria, el contenedor se reinicia solo
#    en vez de quedar colgado
ENV JAVA_OPTS="-Xms64m -Xmx192m -Xss512k \
 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m \
 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:MaxDirectMemorySize=64m \
 -XX:+ExitOnOutOfMemoryError \
 -Djava.awt.headless=true -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
