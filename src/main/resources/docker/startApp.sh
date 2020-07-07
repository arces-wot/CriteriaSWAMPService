#echo hello > /proc/1/fd/1 2>/proc/1/fd/2
printenv
cd /
/usr/local/openjdk-11/bin/java -jar /criteria.jar > /proc/1/fd/1 2>/proc/1/fd/2