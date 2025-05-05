#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Windows-side script for EdenScope plant health monitoring.
This script fetches Mac images from AWS S3, processes them using the plant disease detection model,
and uploads the processed images back to S3.

Usage:
1. Run this script to automatically fetch, process, and upload images
"""

import os
import time
import boto3
import logging
import sys
import glob
import mimetypes
import argparse
from pathlib import Path

# Add the PlantsDiseaseDetection directory to the path
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
DISEASE_DETECTION_DIR = os.path.join(CURRENT_DIR, "PlantsDiseaseDetection")
sys.path.append(DISEASE_DETECTION_DIR)

# Import the batch processing functionality
from process_all_users_images import process_all_users

# Configure logging
logging.basicConfig(
    level=logging.INFO, 
    format="%(asctime)s %(levelname)s %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('win_s3_process_images.log')
    ]
)

# Constants
BUCKET_NAME = "edenscopemacbucket"
MAC_IMAGE_FOLDER = os.path.join(CURRENT_DIR, "MAC_Image")
MAC_USER_ID = "mac_user"  # Identifier for images uploaded from Mac
S3_RAW_PREFIX = "raw/"    # Prefix for raw images
S3_PROCESSED_PREFIX = "processed/"  # Prefix for processed images
POLL_INTERVAL = 5  # seconds

def ensure_directories_exist():
    """Create the local directories if they don't exist."""
    # Create the main MAC_Image folder
    os.makedirs(MAC_IMAGE_FOLDER, exist_ok=True)
    
    # Create the user-specific folder
    user_folder = os.path.join(MAC_IMAGE_FOLDER, MAC_USER_ID)
    os.makedirs(user_folder, exist_ok=True)
    
    # Create the processed folder
    processed_folder = os.path.join(user_folder, "Processed_Image")
    os.makedirs(processed_folder, exist_ok=True)
    
    logging.info(f"Ensured directories exist: {MAC_IMAGE_FOLDER} with user folder and processed subfolder")

def get_s3_keys(s3, prefix):
    """Return a set of all S3 keys (excluding directories) under the given prefix."""
    s3_keys = set()
    response = s3.list_objects_v2(Bucket=BUCKET_NAME, Prefix=prefix)
    if "Contents" in response:
        for obj in response["Contents"]:
            key = obj["Key"]
            if key.endswith("/") or key == prefix:
                continue
            s3_keys.add(key)
    return s3_keys

def download_new_files(s3, s3_keys):
    """
    Download S3 objects that do not already exist locally.
    
    Returns:
        bool: True if any new files were downloaded, False otherwise
    """
    new_files_downloaded = False
    
    for key in s3_keys:
        # Expect key format: raw/<user_id>/<filename>
        parts = key.split('/')
        if len(parts) < 3:
            continue
        user_folder_name = parts[1]
        filename = parts[2]

        local_user_folder = os.path.join(MAC_IMAGE_FOLDER, user_folder_name)
        if not os.path.exists(local_user_folder):
            os.makedirs(local_user_folder)
            logging.info(f"Created local folder: {local_user_folder}")

        local_path = os.path.join(local_user_folder, filename)
        if os.path.exists(local_path):
            # Check if the sizes match
            try:
                head = s3.head_object(Bucket=BUCKET_NAME, Key=key)
                if head['ContentLength'] == os.path.getsize(local_path):
                    logging.info(f"File already exists locally with same size: {local_path}, skipping.")
                    continue
            except Exception:
                # If error checking, proceed with download to be safe
                pass

        try:
            logging.info(f"Downloading s3://{BUCKET_NAME}/{key} to {local_path}")
            s3.download_file(BUCKET_NAME, key, local_path)
            new_files_downloaded = True
        except Exception as e:
            logging.error(f"Error downloading {key}: {e}")
    
    return new_files_downloaded

def upload_processed_images_to_s3(s3):
    """
    Upload processed images to S3 so they can be accessed by the Mac app.
    """
    try:
        logging.info("Uploading processed images to S3...")
        
        # Get list of user folders
        user_folders = [d for d in os.listdir(MAC_IMAGE_FOLDER) 
                       if os.path.isdir(os.path.join(MAC_IMAGE_FOLDER, d))]
        
        upload_count = 0
        
        for user_folder in user_folders:
            # Path to user's processed images folder
            processed_folder = os.path.join(MAC_IMAGE_FOLDER, user_folder, "Processed_Image")
            
            # Skip if folder doesn't exist
            if not os.path.exists(processed_folder):
                continue
            
            # Get all processed images
            processed_images = glob.glob(os.path.join(processed_folder, "*_processed*"))
            
            for img_path in processed_images:
                # Get just the filename
                filename = os.path.basename(img_path)
                
                # Create the S3 key
                s3_key = f"{S3_PROCESSED_PREFIX}{user_folder}/{filename}"
                
                mime_type, _ = mimetypes.guess_type(img_path)
                if not mime_type:
                    # Fallback if Python can't guess it
                    mime_type = "application/octet-stream"
                
                try:
                    # Check if already uploaded (if it has the same size)
                    try:
                        head = s3.head_object(Bucket=BUCKET_NAME, Key=s3_key)
                        # If size matches, skip uploading
                        if head['ContentLength'] == os.path.getsize(img_path):
                            logging.info(f"Processed image already in S3 with same size: {s3_key}")
                            continue
                    except Exception:
                        # Object doesn't exist or error occurred, proceed with upload
                        pass
                    
                    # Upload the processed image to S3
                    logging.info(f"Uploading processed image to S3: {s3_key}")
                    s3.upload_file(img_path, BUCKET_NAME, s3_key, ExtraArgs={
                        "ContentType": mime_type,
                        "ContentDisposition": "inline"
                    })
                    upload_count += 1
                except Exception as e:
                    logging.error(f"Error uploading processed image {img_path} to S3: {e}")
        
        logging.info(f"Uploaded {upload_count} processed images to S3")
        return upload_count
        
    except Exception as e:
        logging.error(f"Error during processed image upload: {e}")
        return 0

def process_images():
    """Process all images in MAC_Image folder using the plant disease detection model."""
    try:
        logging.info("Starting plant disease detection processing on Mac images...")
        
        # Process all user images
        process_all_users(
            user_image_dir=MAC_IMAGE_FOLDER,
            weights_path=None,  # Use default weights
            conf_threshold=0.25,
            iou_threshold=0.5
        )
        
        logging.info("Plant disease detection processing complete.")
        
    except Exception as e:
        logging.error(f"Error during image processing: {e}")

def main():
    """Main function to run the script in a loop or one-time."""
    parser = argparse.ArgumentParser(description='Fetch, process, and upload Mac images for plant disease detection.')
    parser.add_argument('--once', action='store_true', help='Run once and exit (default: loop continuously)')
    parser.add_argument('--interval', type=int, default=POLL_INTERVAL, help=f'Poll interval in seconds (default: {POLL_INTERVAL})')
    args = parser.parse_args()
    
    poll_interval = args.interval
    
    logging.info("Starting Windows S3 Process Images script...")
    ensure_directories_exist()
    
    # Initialize S3 client
    s3 = boto3.client('s3')
    
    # Check if bucket exists
    try:
        s3.head_bucket(Bucket=BUCKET_NAME)
        logging.info(f"S3 bucket {BUCKET_NAME} exists")
    except Exception as e:
        logging.error(f"S3 bucket {BUCKET_NAME} doesn't exist: {e}")
        logging.error("Please make sure the bucket is created from the Mac side first")
        return
    
    # Flag to track if new files were downloaded in the current poll
    new_files_downloaded = False
    
    # Main loop
    try:
        while True:
            # Get images from S3
            logging.info(f"Listing objects in s3://{BUCKET_NAME}/{S3_RAW_PREFIX}")
            raw_s3_keys = get_s3_keys(s3, S3_RAW_PREFIX)
            
            # Download new or updated files
            new_files = download_new_files(s3, raw_s3_keys)
            if new_files:
                new_files_downloaded = True
            
            # Process images if new files were downloaded
            if new_files_downloaded:
                process_images()
                # Upload processed images to S3
                upload_processed_images_to_s3(s3)
                new_files_downloaded = False  # Reset the flag
            
            # If --once flag is set, exit after one iteration
            if args.once:
                logging.info("Completed one-time execution. Exiting.")
                break
            
            logging.info(f"Sleeping for {poll_interval} seconds before next poll...")
            time.sleep(poll_interval)
    
    except KeyboardInterrupt:
        logging.info("Process interrupted by user. Exiting.")
    
    except Exception as e:
        logging.error(f"Unexpected error: {e}")

if __name__ == "__main__":
    main()