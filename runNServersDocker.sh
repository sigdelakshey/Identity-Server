#!/bin/bash
sudo make docker-build
for i in $(seq 1 $1)
do
  sudo make docker-server PORT=$((5186+i)) &
done
echo $1 servers started