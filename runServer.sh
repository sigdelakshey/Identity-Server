#!/bin/bash
redis-server --port 5186 --daemonize yes &
str="$*"
make server ARGS="$str"