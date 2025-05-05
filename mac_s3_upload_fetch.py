#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mac-side script for EdenScope plant health monitoring.
This script uploads images to AWS S3 and fetches processed images from S3.

Usage:
1. Place images in the folder specified by MAC_IMAGE_FOLDER
2. Run this script to upload them to S3
3. The Windows computer will process the images
4. Run this script again to fetch the processed images to MAC_PROCESSED_FOLDER
"""

import os
import time
import boto3
import logging
import glob
import mimetypes
import argparse
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO, 
    format="%(asctime)s %(levelname)s %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('mac_s3_upload_fetch.log')
    ]
)

# Constants
BUCKET_NAME = "edenscopemacbucket"
MAC_IMAGE_FOLDER = os.path.expanduser("~/EdenScopePortal/Upload_Image")
MAC_PROCESSED_FOLDER = os.path.expanduser("~/EdenScopePortal/Processed_Image")
MAC_USER_ID = "mac_user"  # Identifier for images uploaded from Mac
S3_RAW_PREFIX = "raw/"    # Prefix for raw images
S3_PROCESSED_PREFIX = "processed/"  # Prefix for processed images
POLL_INTERVAL = 30  # seconds

def ensure_directories_exist():
    """Create the local directories if they don't exist."""
    os.makedirs(MAC_IMAGE_FOLDER, exist_ok=True)
    os.makedirs(MAC_PROCESSED_FOLDER, exist_ok=True)
    logging.info(f"Ensured directories exist: {MAC_IMAGE_FOLDER} and {MAC_PROCESSED_FOLDER}")

def upload_images_to_s3(s3):
    """
    Upload images from MAC_IMAGE_FOLDER to S3.
    """
    try:
        logging.info(f"Checking for new images in {MAC_IMAGE_FOLDER}...")
        
        # Get list of image files
        image_extensions = ['.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff']
        image_files = []
        
        for file in os.listdir(MAC_IMAGE_FOLDER):
            file_path = os.path.join(MAC_IMAGE_FOLDER, file)
            if os.path.isfile(file_path) and any(file.lower().endswith(ext) for ext in image_extensions):
                image_files.append(file_path)
        
        if not image_files:
            logging.info("No images found to upload.")
            return 0
        
        upload_count = 0
        for img_path in image_files:
            filename = os.path.basename(img_path)
            
            # Create the S3 key with MAC_USER_ID as the folder name
            s3_key = f"{S3_RAW_PREFIX}{MAC_USER_ID}/{filename}"
            
            # Guess the MIME type
            mime_type, _ = mimetypes.guess_type(img_path)
            if not mime_type:
                mime_type = "application/octet-stream"
                
            try:
                # Check if already uploaded with same size
                try:
                    head = s3.head_object(Bucket=BUCKET_NAME, Key=s3_key)
                    # If size matches, skip uploading
                    if head['ContentLength'] == os.path.getsize(img_path):
                        logging.info(f"Image already in S3 with same size: {s3_key}")
                        continue
                except Exception:
                    # Object doesn't exist or error occurred, proceed with upload
                    pass
                
                # Upload the image to S3
                logging.info(f"Uploading image to S3: {s3_key}")
                s3.upload_file(img_path, BUCKET_NAME, s3_key, ExtraArgs={
                    "ContentType": mime_type,
                    "ContentDisposition": "inline"
                })
                upload_count += 1
            except Exception as e:
                logging.error(f"Error uploading image {img_path} to S3: {e}")
        
        logging.info(f"Uploaded {upload_count} images to S3")
        return upload_count
    
    except Exception as e:
        logging.error(f"Error during image upload: {e}")
        return 0

def fetch_processed_images(s3):
    """
    Fetch processed images from S3 to MAC_PROCESSED_FOLDER.
    """
    try:
        logging.info("Checking for new processed images in S3...")
        
        # Get list of processed images for the MAC_USER_ID
        processed_prefix = f"{S3_PROCESSED_PREFIX}{MAC_USER_ID}/"
        processed_keys = []
        
        # List objects with the processed prefix
        response = s3.list_objects_v2(Bucket=BUCKET_NAME, Prefix=processed_prefix)
        if "Contents" in response:
            for obj in response["Contents"]:
                key = obj["Key"]
                if key.endswith("/") or key == processed_prefix:
                    continue
                processed_keys.append(key)
        
        if not processed_keys:
            logging.info("No processed images found in S3.")
            return 0
        
        download_count = 0
        for key in processed_keys:
            # Extract filename from the key
            filename = os.path.basename(key)
            local_path = os.path.join(MAC_PROCESSED_FOLDER, filename)
            
            # Skip if already downloaded with the same size
            if os.path.exists(local_path):
                try:
                    head = s3.head_object(Bucket=BUCKET_NAME, Key=key)
                    if head['ContentLength'] == os.path.getsize(local_path):
                        logging.info(f"Processed image already downloaded with same size: {filename}")
                        continue
                except Exception:
                    # If error checking, proceed with download to be safe
                    pass
            
            try:
                logging.info(f"Downloading processed image from S3: {key} to {local_path}")
                s3.download_file(BUCKET_NAME, key, local_path)
                download_count += 1
            except Exception as e:
                logging.error(f"Error downloading processed image {key}: {e}")
        
        logging.info(f"Downloaded {download_count} processed images from S3")
        return download_count
    
    except Exception as e:
        logging.error(f"Error during processed image fetch: {e}")
        return 0

def main():
    """Main function to run the script in a loop or one-time."""
    parser = argparse.ArgumentParser(description='Upload images to AWS S3 and fetch processed images.')
    parser.add_argument('--once', action='store_true', help='Run once and exit (default: loop continuously)')
    parser.add_argument('--interval', type=int, default=POLL_INTERVAL, help=f'Poll interval in seconds (default: {POLL_INTERVAL})')
    args = parser.parse_args()
    
    poll_interval = args.interval
    
    logging.info("Starting Mac S3 Upload/Fetch script...")
    ensure_directories_exist()
    
    # Initialize S3 client
    s3 = boto3.client('s3')
    
    # Check if bucket exists, create if it doesn't
    try:
        s3.head_bucket(Bucket=BUCKET_NAME)
        logging.info(f"S3 bucket {BUCKET_NAME} exists")
    except Exception:
        logging.info(f"S3 bucket {BUCKET_NAME} doesn't exist, creating it...")
        try:
            s3.create_bucket(Bucket=BUCKET_NAME)
            logging.info(f"Created S3 bucket {BUCKET_NAME}")
        except Exception as e:
            logging.error(f"Error creating S3 bucket {BUCKET_NAME}: {e}")
            return
    
    # Main loop
    try:
        while True:
            # Upload images
            upload_images_to_s3(s3)
            
            # Fetch processed images
            fetch_processed_images(s3)
            
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