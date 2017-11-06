@echo off
:: Install Logback as logging backend for Payara.
:: The command requires asadmin to be present on the PATH, and the server to be running.
:: Things like upgrading / undeploying are not considered at this moment

call asadmin add-library --type ext payara-logback-libs.jar
call asadmin deploy --type osgi payara-logback-delegation.jar
call asadmin deploy --type osgi payara-logback-access.jar

:: find out instance root
for /f "tokens=2 delims== " %%F in ('asadmin generate-jvm-report ^| find "com.sun.aas.instanceRoot ="') do set CONFIG_DIR=%%F\config

copy defaults\*.xml %CONFIG_DIR%

call asadmin create-jvm-options "-Dlogback.configurationFile=${com.sun.aas.instanceRoot}/config/logback.xml"
call asadmin set-log-attributes "handlers=org.slf4j.bridge.SLF4JBridgeHandler"
call asadmin create-jvm-options "-DlogbackAccess.configurationFile=${com.sun.aas.instanceRoot}/config/logback-access.xml"
:: enable access logging for default virtual server port
call asadmin set server-config.http-service.virtual-server.server.property.valve_1="io.github.goodees.payara.logback.access.Logger"
:: also enable it for any future configuration that is created
call asadmin set default-config.http-service.virtual-server.server.property.valve_1="io.github.goodees.payara.logback.access.Logger"
call asadmin restart-domain