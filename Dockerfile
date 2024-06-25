FROM fedora:latest as base
RUN yum install -y java-21-openjdk-headless

FROM base as build
RUN <<EOF
yum install -y java-21-openjdk-devel unzip zip
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.7
EOF
COPY ./build.gradle /p4/
WORKDIR /p4
RUN $HOME/.sdkman/candidates/gradle/8.7/bin/gradle clean build
COPY . /p4
RUN ./gradlew -PmainClass='identity.server.IdServer' installDist --no-daemon

FROM base
RUN yum install -y redis
COPY --from=build /p4/build/install/p4 /p4
COPY --from=build /p4/src/identity/resources /p4/src/identity/resources
WORKDIR /p4
ENV PORT=5185
ENV ARGS=""
ENTRYPOINT redis-server --port 5186 --daemonize yes & bin/p4 -n $PORT $ARGS