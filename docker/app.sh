#!/bin/sh
# Manages the Redis + Tomcat containers for jee6-ejb-examples.
#   app.sh up       start Redis and Tomcat, deploy the WAR
#   app.sh deploy   (re)deploy web/build/libs/jee6-ejb-examples.war and wait for it
#   app.sh clients  run both remote clients against the containers' Redis
#   app.sh logs     follow the Tomcat log
#   app.sh down     stop and remove the containers and the Redis data volume
# The WAR and the client distribution must already be built (the Gradle app*
# tasks do that).
set -eu

cd "$(dirname "$0")"
COMPOSE="docker compose -f compose.yaml"
APP=jee6-ejb-examples
WAR="../web/build/libs/$APP.war"
WEBAPPS=/usr/local/tomcat/webapps
URL="http://localhost:8080/$APP/"
CLIENT_LIB="../client/build/install/client/lib"

fail() { echo "app.sh: $*" >&2; exit 1; }

deploy() {
    [ -f "$WAR" ] || fail "$WAR not found; run ./gradlew buildWar first"
    since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    echo "Deploying $APP.war ..."
    # Stream the WAR in, then rename it into place in one step, so Tomcat's
    # auto-deployer never picks up a half-copied file.
    $COMPOSE exec -T tomcat sh -c "cat > $WEBAPPS/.$APP.upload && mv $WEBAPPS/.$APP.upload $WEBAPPS/$APP.war" < "$WAR"

    i=0
    while [ $i -lt 60 ]; do
        log=$($COMPOSE logs --no-log-prefix --since "$since" tomcat 2>/dev/null || true)
        if echo "$log" | grep -q "Deployment of web application archive \[$WEBAPPS/$APP.war\] has finished"; then
            if curl -fs -o /dev/null "$URL"; then
                echo "Deployed: $URL"
                return 0
            fi
            echo "$log" | tail -40 >&2
            fail "the application did not start (see above, and ./gradlew appLogs)"
        fi
        i=$((i + 1)); sleep 1
    done
    fail "timed out waiting for the deployment (see ./gradlew appLogs)"
}

case "${1:-}" in
    up)
        $COMPOSE up -d --wait
        deploy
        ;;
    deploy)  deploy ;;
    clients)
        [ -d "$CLIENT_LIB" ] || fail "$CLIENT_LIB not found; run ./gradlew :client:installDist first"
        for main in ShoppingCartRemoteClient CurrencyConverterRemoteClient; do
            echo "== $main"
            java -Dredis.address=redis://localhost:6379 -cp "$CLIENT_LIB/*" "com.example.client.$main"
        done
        ;;
    logs)    $COMPOSE logs -f tomcat ;;
    down)    $COMPOSE down -v ;;
    *)       fail "usage: app.sh up|deploy|clients|logs|down" ;;
esac
