# EdenScope: AI-Driven Plant Health Monitoring Payload

An advanced plant health monitoring system that uses computer vision and machine learning to detect and diagnose plant diseases.

## Project Overview

EdenScope is a comprehensive solution for monitoring plant health, combining hardware and software components:
- Sony camera integration for high-quality image capture
- YOLOv8/YOLOv5 based plant disease detection models
- AWS cloud integration for remote image processing and storage
- Mobile application for user interaction and results visualization

## Repository Structure

- **PlantsDiseaseDetection/**: Main disease detection models and web interface
- **PlantsDiseaseDetection_V12X/**: Enhanced version with YOLOv12X implementation
- **Sony_Camera_SDK**: Camera control and image capture functionality
- **Shell_Script & System_Service/**: System services for automation
- **User_Image/**: Storage for captured and processed user images
- **MAC_Image/**: Test and development image set

## Key Features

- Real-time plant disease detection
- Multi-model support (YOLOv5, YOLOv8, YOLOv12X)
- Automatic image capture and processing
- Cloud-based image storage and processing
- User-specific image management
- Mobile application integration

## Setup and Requirements

See `requirements.txt` in the PlantsDiseaseDetection directory for Python dependencies.

## Usage

The system can be used in multiple modes:
- Run `run_main_web.py` to start the web interface
- Use `S3_Poll_Image.py` for AWS S3 integration
- Execute `edenscope_trigger_camera_capture.sh` for automated image capture

## License

[License information]