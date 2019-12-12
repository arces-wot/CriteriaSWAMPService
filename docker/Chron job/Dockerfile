# Alpine Linux with OpenJDK JRE
FROM openjdk:8-jre-alpine


# copy application files
COPY build/libs/weatherAgent-1.0-SNAPSHOT.jar /weatherAgent.jar
COPY aggregator.jsap.json /aggregator.jsap.json
COPY tokens/StoredCredential /tokens/StoredCredential
COPY startApp.sh /startApp.sh

RUN chmod 777 startApp.sh

# Copy hello-cron file to the cron.d directory
COPY cron-job /etc/cron.d/cron-job

# Give execution rights on the cron job
RUN chmod 0644 /etc/cron.d/cron-job

# Apply cron job
RUN crontab /etc/cron.d/cron-job

# run application with this command line
#CMD ["/usr/bin/java", "-jar", "/weatherAgent.jar"]
CMD ["crond", "-f", "-d", "8"]