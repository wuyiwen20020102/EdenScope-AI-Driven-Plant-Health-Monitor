# EdenScope: AI-Driven Plant Health Monitoring Payload

EdenScope is a comprehensive plant health monitoring system that uses computer vision, advanced AI models, and cloud integration to detect and diagnose plant diseases in real-time.

## System Architecture

### Hardware Components
- **Sony Camera Integration**: Uses Sony Camera Remote SDK V1.13 for high-quality image capture
- **Raspberry Pi**: Acts as the processing unit for camera control and remote operation
- **Mobile Device**: Runs the companion application for viewing results and controlling the system

### Software Components
- **Plant Disease Detection Models**: 
  - YOLOv5/YOLOv8-based object detection for identifying plant diseases
  - Enhanced YOLOv12X implementation for improved accuracy
  - Trained on custom plant disease datasets

- **Cloud Integration**:
  - AWS IoT Core for device management and remote control
  - AWS S3 for image storage and retrieval
  - AWS Amplify for mobile backend

- **Mobile Application**: 
  - Android app built with Kotlin and Jetpack Compose
  - Real-time results visualization
  - Manual and scheduled image capture

## Directory Structure

### Key Directories
- **PlantsDiseaseDetection/**: Main disease detection models
  - `YOLOv8v5Model.py`: Core model implementation
  - `Recognition_UI.py`: Streamlit-based web interface
  - `run_main_web.py`: Web interface launcher
  - `datasets/`: Contains plant disease datasets

- **PlantsDiseaseDetection_V12X/**: Enhanced model implementation
  - `YOLOv12XModel.py`: Advanced YOLO model with auto-update capability
  - Same interface as base implementation but with improved model

- **Sony_Camera_SDK_V1.13_Linux64_ARMv8_Edenscope/**: Camera control SDK
  - `app/`: Camera control application
  - `app/Edenscope_Capture_Image.cpp`: Main camera capture functionality

- **Shell_Script & System_Service/**: System automation
  - `edenscope_trigger_camera_capture.service`: Systemd service for camera operation
  - `edenscope_trigger_camera_capture.sh`: Camera capture script
  - `process_all_users_images.sh`: Batch image processing script

- **aws-iot-device-sdk-python-v2/**: AWS IoT integration
  - `samples/edenscope_trigger_camera_capture.py`: IoT-based camera trigger

- **PlantHealthMonitorApplication/**: Android mobile application
  - Kotlin-based application using Jetpack Compose
  - AWS Amplify integration

- **User_Image/**: Storage for user-specific images
  - Organized by user ID
  - Contains original and processed images

## Setup Requirements

### Software Requirements
```
# Python Dependencies (from requirements.txt)
opencv-python==4.7.0.72
numpy==1.24.3
torch==1.13.1
torchvision==0.14.1
streamlit==1.29.0
boto3
PySide6==6.5.1.1
ultralytics>=8.2.0
QtFusion==0.5.4
```

### Camera SDK Requirements
- For Linux:
  ```
  sudo apt install autoconf libtool libudev-dev gcc g++ make cmake unzip libxml2-dev
  ```
- For Windows:
  - Visual Studio 2019 or later
  - Windows SDK 10.0.17763.0
  - libusbK 3.0 driver
  - CMake

### AWS Configuration
- AWS IoT Core and S3 credentials
- Properly configured certificates and policies

## Usage Instructions

### Running the Web Interface
```bash
cd PlantsDiseaseDetection
python run_main_web.py
```

### Processing Images from S3
```bash
# Process images for all users
python process_all_users_images.py

# Process images with the V12X model
python process_all_users_images_V12X.py
```

### Camera Operations
```bash
# Trigger camera capture via shell script
./Shell_Script\ \&\ System_Service/edenscope_trigger_camera_capture.sh

# Deploy as a system service
sudo cp ./Shell_Script\ \&\ System_Service/edenscope_trigger_camera_capture.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable edenscope_trigger_camera_capture.service
sudo systemctl start edenscope_trigger_camera_capture.service
```

### Manual Testing
```bash
# Test with a single image
cd PlantsDiseaseDetection
python run_test_image.py

# Test with camera feed
python run_test_camera.py

# Test with video
python run_test_video.py
```

## YOLO Models

The system uses two primary model implementations:

1. **YOLOv8/v5 Model**: Base implementation with good performance
   - Located in `PlantsDiseaseDetection/YOLOv8v5Model.py`
   - Pretrained weights in `weights/` directory

2. **YOLOv12X Model**: Enhanced implementation with auto-updating capability
   - Located in `PlantsDiseaseDetection_V12X/YOLOv12XModel.py`
   - Checks and updates Ultralytics package if needed
   - Advanced features for improved accuracy

## AWS IoT Integration

The system connects to AWS IoT Core to enable remote triggering of image capture and processing:

1. Camera subscribes to the `edenscope/camera-capture-photo` MQTT topic
2. Mobile app publishes to this topic to trigger image capture
3. Captured images are automatically uploaded to S3 
4. S3 polling scripts detect new images and process them
5. Results are stored back in S3 for mobile app access

## Deployment

The system is designed to run as a set of systemd services on Linux:

- `edenscope_trigger_camera_capture.service`: Listens for capture commands via MQTT
- `edenscope_process_images.service`: Processes new images periodically
- `edenscope_image_cleanup.service`: Manages disk space by cleaning up old images

## License

[License information]

## Contributors

[Contributor information]