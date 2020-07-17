export JAVA_HOME=$(realpath /usr/bin/javadoc | sed 's@bin/javadoc$@@')

mvn -P release -DskipTests deploy