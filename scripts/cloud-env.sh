# Source this file only in the prepared Codex Cloud workspace.
# Windows/local development uses your installed JDK/Maven; see README.md.
export JAVA_HOME=/workspace/tools/jdk-21.0.9+10
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
export MAVEN_USER_HOME=/workspace/.cache/maven-wrapper
export npm_config_cache=/workspace/.cache/npm
