#!/bin/sh
if [ -f /tmp/env_vars ]; then
. /tmp/env_vars
fi

exec java $JAVA_OPTS -Dserver.port=8080 org.springframework.boot.loader.JarLauncher
