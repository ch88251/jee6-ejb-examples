#!/bin/sh
# Manages the JBoss EAP 6.4 container for jee6-ejb-examples.
#   eap.sh up       build the image (if needed), start EAP, deploy the EAR
#   eap.sh deploy   (re)deploy ear/build/libs/jee6-ejb-examples.ear and wait for the result
#   eap.sh clients  run both remote EJB clients in a container
#   eap.sh logs     follow the server log
#   eap.sh down     stop and remove the containers
# The EAR and client jar must already be built (the Gradle eap* tasks do that).
set -eu

cd "$(dirname "$0")"
COMPOSE="docker compose -f compose.yaml"
APP=jee6-ejb-examples.ear
EAR="../ear/build/libs/$APP"
DEPLOYMENTS=/opt/jboss-eap-6.4/standalone/deployments
EAP_ZIP="${EAP_ZIP:-jboss-eap-6.4.0.zip}"
export EAP_ZIP

fail() { echo "eap.sh: $*" >&2; exit 1; }

deploy() {
    [ -f "$EAR" ] || fail "$EAR not found; run ./gradlew buildEar first"
    echo "Deploying $APP ..."
    # Stream the EAR in as the jboss user, then rename it into place in one step,
    # so the deployment scanner never picks up a half-copied file.
    $COMPOSE exec -T eap sh -c "cat > $DEPLOYMENTS/.$APP.upload && mv $DEPLOYMENTS/.$APP.upload $DEPLOYMENTS/$APP" < "$EAR"

    # The scanner writes <app>.deployed or <app>.failed when it's done; wait for
    # a marker newer than the EAR just copied.
    i=0
    while [ $i -lt 120 ]; do
        result=$($COMPOSE exec -T eap sh -c "cd $DEPLOYMENTS; \
            if [ $APP.deployed -nt $APP ]; then echo deployed; \
            elif [ $APP.failed -nt $APP ]; then echo failed; fi" || true)
        case "$result" in
            deployed) echo "Deployed: http://localhost:8080/jee6-ejb-examples/"; return 0 ;;
            failed)   $COMPOSE exec -T eap cat "$DEPLOYMENTS/$APP.failed" >&2 || true
                      fail "deployment failed (see above, and ./gradlew eapLogs)" ;;
        esac
        i=$((i + 1)); sleep 1
    done
    fail "timed out waiting for the deployment (see ./gradlew eapLogs)"
}

case "${1:-}" in
    up)
        if ! docker image inspect jee6-ejb-examples-eap:6.4 > /dev/null 2>&1 && [ ! -f "$EAP_ZIP" ]; then
            fail "docker/$EAP_ZIP not found. Download JBoss EAP 6.4 from https://developers.redhat.com,
put the zip in the docker/ folder, and pass -PeapZip=<file name> if it isn't $EAP_ZIP."
        fi
        $COMPOSE up -d --build eap
        echo "Waiting for EAP to start ..."
        i=0
        until $COMPOSE exec -T eap sh -c '"$JBOSS_HOME/bin/jboss-cli.sh" --connect --command=":read-attribute(name=server-state)"' 2>/dev/null | grep -q running; do
            i=$((i + 1)); [ $i -lt 90 ] || fail "EAP did not start (see ./gradlew eapLogs)"
            sleep 2
        done
        deploy
        ;;
    deploy)  deploy ;;
    clients)
        $COMPOSE --profile client run --rm client com.example.client.ShoppingCartRemoteClient
        $COMPOSE --profile client run --rm client com.example.client.CurrencyConverterRemoteClient
        ;;
    logs)    $COMPOSE logs -f eap ;;
    down)    $COMPOSE --profile client down ;;
    *)       fail "usage: eap.sh up|deploy|clients|logs|down" ;;
esac
