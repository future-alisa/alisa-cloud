set -eu
cat > /workspace/settings.xml <<'XML'
<settings><proxies><proxy><id>cluster-egress</id><active>true</active><protocol>http</protocol><host>192.168.1.112</host><port>4067</port><nonProxyHosts>localhost|127.0.0.1|*.svc|*.cluster.local|registry.kind.internal</nonProxyHosts></proxy></proxies></settings>
XML
cd /workspace/source-api
mvn -B -s /workspace/settings.xml -Dmaven.repo.local=/workspace/maven -pl alisa-user -am package -DskipTests
mkdir -p /workspace/runtime-api
cp packages/alisa-user-*.jar /workspace/runtime-api/alisa-user.jar
cp deploy/admin/build/Dockerfile.api /workspace/runtime-api/Dockerfile
