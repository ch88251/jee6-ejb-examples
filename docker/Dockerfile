# JBoss EAP 6.4 with the configuration this application needs.
#
# EAP isn't freely redistributable, so its zip isn't included: download
# "Red Hat JBoss Enterprise Application Platform 6.4.0" (jboss-eap-6.4.0.zip) from
# https://developers.redhat.com (free account), put it in this docker/ folder, and
# pass its file name as EAP_ZIP if it differs. Any EAP 6.4.x zip whose top-level
# folder is jboss-eap-6.4/ works.
#
# EAP 6.4 runs on Java 8, which also matches the app's bytecode level (--release 8).
FROM eclipse-temurin:8-jdk

ARG EAP_ZIP=jboss-eap-6.4.0.zip
# Development-only credentials (see README). Override with --build-arg.
ARG ADMIN_USER=admin
ARG ADMIN_PASSWORD=Admin#2026
ARG EJB_USER=ejbuser
ARG EJB_PASSWORD=EjbUser#2026

ENV JBOSS_HOME=/opt/jboss-eap-6.4

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r jboss && useradd -r -g jboss -d /opt -s /usr/sbin/nologin jboss

COPY ${EAP_ZIP} /tmp/eap.zip
RUN unzip -q /tmp/eap.zip -d /opt \
 && rm /tmp/eap.zip \
 && test -x "$JBOSS_HOME/bin/standalone.sh" \
 && chown -R jboss:jboss "$JBOSS_HOME"

USER jboss

# Users: a management user for the web console (port 9990), and an application
# user the remote EJB clients log in with (ApplicationRealm).
RUN "$JBOSS_HOME/bin/add-user.sh" --silent -u "$ADMIN_USER" -p "$ADMIN_PASSWORD" \
 && "$JBOSS_HOME/bin/add-user.sh" --silent -a -u "$EJB_USER" -p "$EJB_PASSWORD"

# Apply configure-eap.cli to standalone-full.xml. EAP 6 has no offline CLI mode,
# so start the server briefly, run the script, and shut it down.
COPY --chown=jboss:jboss configure-eap.cli /tmp/configure-eap.cli
RUN set -e; \
    "$JBOSS_HOME/bin/standalone.sh" -c standalone-full.xml > /tmp/eap-build.log 2>&1 & \
    for i in $(seq 1 60); do \
      if "$JBOSS_HOME/bin/jboss-cli.sh" --connect --command=':read-attribute(name=server-state)' 2>/dev/null | grep -q running; then break; fi; \
      sleep 2; \
    done; \
    "$JBOSS_HOME/bin/jboss-cli.sh" --connect --file=/tmp/configure-eap.cli; \
    "$JBOSS_HOME/bin/jboss-cli.sh" --connect --command=':shutdown'; \
    wait; \
    rm -rf /tmp/configure-eap.cli /tmp/eap-build.log \
           "$JBOSS_HOME/standalone/configuration/standalone_xml_history" \
           "$JBOSS_HOME/standalone/data" "$JBOSS_HOME/standalone/log" "$JBOSS_HOME/standalone/tmp"

# 8080 HTTP, 9990 management console/API, 4447 remote EJB (JBoss Remoting)
EXPOSE 8080 9990 4447

# Healthy once the application answers, i.e. the EAR has been deployed.
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=30 \
    CMD curl -fs http://localhost:8080/jee6-ejb-examples/ > /dev/null || exit 1

# standalone-full: the profile with EJB 2.x CMP, JMS (HornetQ) and IIOP.
CMD ["/bin/sh", "-c", "exec \"$JBOSS_HOME/bin/standalone.sh\" -c standalone-full.xml -b 0.0.0.0 -bmanagement 0.0.0.0"]
