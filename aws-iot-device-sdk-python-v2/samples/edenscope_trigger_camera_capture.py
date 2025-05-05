# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0.


import datetime
import boto3
import glob
import json
import os
import subprocess
import sys
import threading
import time
from awscrt import mqtt, http, io
from awsiot import mqtt_connection_builder
from botocore.exceptions import NoCredentialsError, ClientError


# Parameters - Replace these with your actual values
ENDPOINT = "a28tskmmd67e9b-ats.iot.us-east-1.amazonaws.com"            # Endpoint e.g., "your-endpoint-ats.iot.region.amazonaws.com"
CLIENT_ID = "EdenscopeRaspberryPiCameraClient"
PATH_TO_CERT = os.path.expanduser("~/certs/device.pem.crt")           # Path to your device certificate
PATH_TO_KEY = os.path.expanduser("~/certs/private.pem.key")           # Path to your private key
PATH_TO_ROOT = os.path.expanduser("~/certs/Amazon-root-CA-1.pem")     # Path to the root CA certificate
TOPIC = "edenscope/camera-capture-photo"                              # The MQTT topic to subscribe to


# Callback when connection is accidentally lost.
def on_connection_interrupted(connection, error, **kwargs):
    print("Connection interrupted. error: {}".format(error))


# Callback when an interrupted connection is re-established.
def on_connection_resumed(connection, return_code, session_present, **kwargs):
    print("Connection resumed. return_code: {} session_present: {}".format(return_code, session_present))
    if return_code == mqtt.ConnectReturnCode.ACCEPTED and not session_present:
        print("Session did not persist. Resubscribing to existing topics...")
        resubscribe_future, _ = connection.resubscribe_existing_topics()
        # Cannot synchronously wait for resubscribe result because we're on the connection's event-loop thread,
        # evaluate result with a callback instead.
        resubscribe_future.add_done_callback(on_resubscribe_complete)


def on_resubscribe_complete(resubscribe_future):
    resubscribe_results = resubscribe_future.result()
    print("Resubscribe results: {}".format(resubscribe_results))
    for topic, qos in resubscribe_results['topics']:
        if qos is None:
            sys.exit("Server rejected resubscribe to topic: {}".format(topic))


# Callback when the subscribed topic receives a message
def on_message_received(topic, payload, dup, qos, retain, **kwargs):
    print("Received message from topic '{}': {}".format(topic, payload))
    try:
        message = json.loads(payload.decode('utf-8'))
        action = message.get('action')
        if action == 'capture':
            print("Triggering CapturePhoto...")
            # Execute the CapturePhoto program
            capture_photo_path = os.path.expanduser("~/Desktop/Sony Camera SDK/Sony_Camera_SDK_V1.13_Linux64_ARMv8_Edenscope/build/CapturePhoto")
            image_directory = os.path.expanduser("~/Desktop/Sony Camera SDK/Sony_Camera_SDK_V1.13_Linux64_ARMv8_Edenscope/build/AmazonCloudS3Bucket")
           
            result = subprocess.run([capture_photo_path], capture_output=True, text=True)
            if result.stderr:
                print("CapturePhoto errors:\n", result.stderr)
           
            # Find the latest image file starting with 'EDN'
            image_pattern = os.path.join(image_directory, 'EDN*')
            list_of_files = glob.glob(image_pattern)
            if not list_of_files:
                print("No image files found matching pattern 'EDN*'")
                return
            latest_file = max(list_of_files, key=os.path.getctime)
           
            # Generate the new filename with timestamp
            timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
            new_filename = f'edenscope_raw_image_{timestamp}.jpg'
            new_image_path = os.path.join(image_directory, new_filename)
           
            # Rename the latest image file
            try:
                os.rename(latest_file, new_image_path)
            except OSError as e:
                print(f"Error renaming file: {e}")
                return
           
            # Upload the image to S3
            bucket_name = 'edenscope-raspberrypi-s3bucket'
            if upload_to_s3(new_image_path, bucket_name, new_filename):
                print(f"Successfully uploaded {new_image_path} to S3 bucket {bucket_name}.")
            else:
                print(f"Failed to upload {new_image_path} to S3 bucket {bucket_name}.")
           
        else:
            print("Received unknown action: {}".format(action))
    except Exception as e:
        print("Error processing message: {}".format(e))




# Callback when the connection successfully connects
def on_connection_success(connection, callback_data):
    assert isinstance(callback_data, mqtt.OnConnectionSuccessData)
    print("Connection Successful with return code: {} session present: {}".format(callback_data.return_code, callback_data.session_present))




# Callback when a connection attempt fails
def on_connection_failure(connection, callback_data):
    assert isinstance(callback_data, mqtt.OnConnectionFailureData)
    print("Connection failed with error code: {}".format(callback_data.error))




# Callback when a connection has been disconnected or shutdown successfully
def on_connection_closed(connection, callback_data):
    print("Connection closed")
   
# Upload s3 function
def upload_to_s3(file_path, bucket_name, object_name=None):
    # If S3 object_name was not specified, use the file name
    if object_name is None:
        object_name = os.path.basename(file_path)


    # Create an S3 client
    s3_client = boto3.client('s3')


    try:
        response = s3_client.upload_file(file_path, bucket_name, object_name)
        return True
    except FileNotFoundError:
        print(f"The file {file_path} was not found.")
        return False
    except NoCredentialsError:
        print("AWS credentials not available.")
        return False
    except ClientError as e:
        print(f"ClientError: {e}")
        return False


if __name__ == '__main__':
    # Initialize the MQTT connection
    io.init_logging(getattr(io.LogLevel, io.LogLevel.Error.name), 'stderr')
    # Create the proxy options if the data is present in cmdData
    proxy_options = None
    

    # Create a MQTT connection from the command line data
    mqtt_connection = mqtt_connection_builder.mtls_from_path(
        endpoint=ENDPOINT,
        port=8883,
        cert_filepath=PATH_TO_CERT,
        pri_key_filepath=PATH_TO_KEY,
        ca_filepath=PATH_TO_ROOT,
        on_connection_interrupted=on_connection_interrupted,
        on_connection_resumed=on_connection_resumed,
        client_id=CLIENT_ID,
        clean_session=False,
        keep_alive_secs=30,
        http_proxy_options=proxy_options,
        on_connection_success=on_connection_success,
        on_connection_failure=on_connection_failure,
        on_connection_closed=on_connection_closed)


    print("Connecting to {} with client ID '{}'...".format(ENDPOINT, CLIENT_ID))
    connect_future = mqtt_connection.connect()


    # Future.result() waits until a result is available
    connect_future.result()
    print("Connected!")
   
    # Subscribe
    print("Subscribing to topic '{}'...".format(TOPIC))
    subscribe_future, packet_id = mqtt_connection.subscribe(
        topic=TOPIC,
        qos=mqtt.QoS.AT_LEAST_ONCE,
        callback=on_message_received)


    subscribe_result = subscribe_future.result()
    print("Subscribed with {}".format(str(subscribe_result['qos'])))


    # Keep the script running to listen for messages
    try:
        print("Listening for messages on topic '{}'...".format(TOPIC))
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Interrupted by user.")


    # Disconnect
    print("Disconnecting...")
    disconnect_future = mqtt_connection.disconnect()
    disconnect_future.result()
    print("Disconnected!")
