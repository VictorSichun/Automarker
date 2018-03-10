#! /bin/bash
echo "Restarting Server" >> nohup.out
pkill -f "java .* Server"
sleep 1
nohup start-server.sh &

