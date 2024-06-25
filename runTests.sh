#!/bin/bash
redis-server --port 5186 --loglevel warning &
make test