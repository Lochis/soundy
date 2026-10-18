# Build:  docker build --build-arg JAR=plugin/build/libs/soundy-plugin-1.1.0.jar -t ghcr.io/lochis/lavalink-soundy:4.2.2-1 .
FROM ghcr.io/lavalink-devs/lavalink:4.2.2

ARG JAR=plugin/build/libs/soundy-plugin-1.1.0.jar

USER root
COPY --chown=lavalink:lavalink ${JAR} /opt/Lavalink/plugins/
RUN chmod 775 /opt/Lavalink/plugins
USER lavalink
