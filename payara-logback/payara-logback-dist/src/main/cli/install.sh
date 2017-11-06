#!/bin/sh
# Install Logback as logging backend for Payara.
# The command requires asadmin to be present on the PATH, and the server to be running.
# Things like upgrading / undeploying are not considered at this moment

# for mingw environment use asadmin.bat, but the cp will fail and server will work incorrectly.
ASADMIN=${ASADMIN:-asadmin}

$ASADMIN add-library --type ext payara-logback-libs.jar
$ASADMIN deploy --type osgi payara-logback-delegation.jar
$ASADMIN deploy --type osgi payara-logback-access.jar

# find out instance root
CONFIG_DIR=`$ASADMIN generate-jvm-report | grep "com.sun.aas.instanceRoot =" |  awk '{ print $3 }'`

cp defaults/*.xml $CONFIG_DIR

$ASADMIN create-jvm-options "-Dlogback.configurationFile=\${com.sun.aas.instanceRoot}/config/logback.xml"
$ASADMIN set-log-attributes "handlers=org.slf4j.bridge.SLF4JBridgeHandler"
$ASADMIN create-jvm-options "-DlogbackAccess.configurationFile=\${com.sun.aas.instanceRoot}/config/logback-access.xml"
# enable access logging for default virtual server port
$ASADMIN set server-config.http-service.virtual-server.server.property.valve_1="io.github.goodees.payara.logback.access.Logger"
# also enable it for any future configuration that is created
$ASADMIN set default-config.http-service.virtual-server.server.property.valve_1="io.github.goodees.payara.logback.access.Logger"
$ASADMIN restart-domain