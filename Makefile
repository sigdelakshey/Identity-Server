SERVER_DEFAULT_ARGS=--numport 5185
CLIENT_DEFAULT_ARGS=-h
PORT?=5185

.SILENT: server client test
build:
	./gradlew -PmainClass='identity.server.IdServer' installDist
	mkdir tmp
	mv build/install/p4/bin/p4 tmp/server
	mv build/install/p4/bin/p4.bat tmp/server.bat
	./gradlew -PmainClass='identity.client.IdClient' installDist
	mv build/install/p4/bin/p4 build/install/p4/bin/client
	mv build/install/p4/bin/p4.bat build/install/p4/bin/client.bat
	mv tmp/server build/install/p4/bin/server
	mv tmp/server.bat build/install/p4/bin/server.bat
	rm -rf tmp

server: build
	if [ -z '$(ARGS)' ]; \
	then \
  	build/install/p4/bin/server $(SERVER_DEFAULT_ARGS); \
  	else \
	build/install/p4/bin/server $(ARGS); \
	fi

client: build
	if [ -z '$(ARGS)' ]; \
	then \
  	build/install/p4/bin/client $(CLIENT_DEFAULT_ARGS); \
  	else \
  	build/install/p4/bin/client $(ARGS); \
  	fi

docker-build:
	sudo docker build -t identity-server .

docker-server:
	sudo docker run -ePORT=$(PORT) -eARGS=$(ARGS) -p $(PORT):$(PORT) identity-server

docker-killall:
	sudo docker kill $$(sudo docker ps -q)

test:
	./gradlew test clean

clean:
	./gradlew clean
	redis-cli -p 5186 shutdown &
	rm -f *.rdb
	
.PHONY: test clean docker-build docker-server docker-killall
