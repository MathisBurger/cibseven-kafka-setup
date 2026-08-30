FROM cibseven/cibseven:tomcat-latest AS engine-libs

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
RUN mkdir -p /opt/engine-libs
COPY --from=engine-libs /camunda/lib/cibseven-engine-[0-9]*.jar /opt/engine-libs/cibseven-engine.jar
COPY --from=engine-libs /camunda/lib/cibseven-connect-core-*.jar /opt/engine-libs/cibseven-connect-core.jar
COPY --from=engine-libs /camunda/lib/cibseven-connect-connectors-all-*.jar /opt/engine-libs/cibseven-connect-connectors-all.jar
COPY --from=engine-libs /camunda/lib/cibseven-engine-plugin-connect-*.jar /opt/engine-libs/cibseven-engine-plugin-connect.jar
COPY pom.xml .
COPY src ./src
RUN mvn -q -B -Dengine.libs.dir=/opt/engine-libs clean package

FROM cibseven/cibseven:tomcat-latest
COPY --from=build /build/target/businesskey-connector-plugin.jar /camunda/lib/
RUN awk '\
  { print } \
  /<class>org\.cibseven\.connect\.plugin\.impl\.ConnectProcessEnginePlugin<\/class>/ { seen=1 } \
  seen && /<\/plugin>/ { \
    print "      <plugin>"; \
    print "        <class>org.cibseven.connect.plugin.impl.BusinessKeyConnectorPlugin</class>"; \
    print "      </plugin>"; \
    seen=0 \
  } \
' /camunda/conf/bpm-platform.xml > /tmp/bpm-platform.xml \
  && grep -q BusinessKeyConnectorPlugin /tmp/bpm-platform.xml \
  && mv /tmp/bpm-platform.xml /camunda/conf/bpm-platform.xml
