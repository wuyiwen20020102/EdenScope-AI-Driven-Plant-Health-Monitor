#!/bin/bash

while true; do
    /usr/bin/python3 /home/edenscope/aws-iot-device-sdk-python-v2/samples/cleanup_image.py >> /home/edenscope/cleanup_image.log 2>&1
    sleep 10
done
