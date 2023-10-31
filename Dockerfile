FROM hseeberger/scala-sbt:graalvm-ce-21.3.0-java11_1.6.1_3.1.0

WORKDIR automated-config
COPY . .
RUN sbt compile
CMD sbt "project app" run
EXPOSE 8080