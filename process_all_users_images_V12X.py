#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Multi-user image processor for EdenScope plant health monitoring.
This script processes all images in each user folder within the User_Image directory,
adding bounding boxes for detected diseases and saving to Processed_Image folders.
"""

import os
import sys
import argparse
from pathlib import Path
import time
import logging

# Import the batch processing module
sys.path.append(str(Path(__file__).parent / "PlantsDiseaseDetection"))
from PlantsDiseaseDetection_V12X.batch_process_images import setup_model, process_image

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('edenscope_image_processing_V12X.log')
    ]
)
logger = logging.getLogger('edenscope')

def process_user_folder(user_folder, model, colors, conf_threshold=0.25, iou_threshold=0.5):
    """
    Process all images in a user's folder.
    
    Args:
        user_folder (str): Path to user's folder
        model: The detection model
        colors: Color map for different classes
        conf_threshold (float): Confidence threshold for detections
        iou_threshold (float): IOU threshold for NMS
        
    Returns:
        tuple: (processed_count, total_count) - number of processed images and total images
    """
    # Create processed images folder if it doesn't exist
    processed_folder = os.path.join(user_folder, "Processed_Image")
    os.makedirs(processed_folder, exist_ok=True)
    
    # Get list of image files
    image_extensions = ['.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff']
    image_files = []
    
    for file in os.listdir(user_folder):
        file_path = os.path.join(user_folder, file)
        if os.path.isfile(file_path) and any(file.lower().endswith(ext) for ext in image_extensions):
            image_files.append(file_path)
    
    # Skip if no images found
    if not image_files:
        logger.info(f"No image files found in {user_folder}")
        return 0, 0
    
    logger.info(f"Found {len(image_files)} images to process in {os.path.basename(user_folder)}")
    
    # Process each image
    processed_count = 0
    for image_path in image_files:
        if process_image(image_path, model, colors, processed_folder):
            processed_count += 1
    
    return processed_count, len(image_files)

def process_all_users(user_image_dir, weights_path=None, conf_threshold=0.25, iou_threshold=0.5):
    """
    Process images for all users in the User_Image directory.
    
    Args:
        user_image_dir (str): Path to User_Image directory
        weights_path (str): Path to model weights file
        conf_threshold (float): Confidence threshold for detections
        iou_threshold (float): IOU threshold for NMS
    """
    start_time = time.time()
    
    # Validate User_Image directory
    if not os.path.isdir(user_image_dir):
        logger.error(f"Error: User_Image directory '{user_image_dir}' does not exist")
        return
    
    # Get list of user folders
    user_folders = []
    for item in os.listdir(user_image_dir):
        item_path = os.path.join(user_image_dir, item)
        if os.path.isdir(item_path):
            user_folders.append(item_path)
    
    # Check if user folders were found
    if not user_folders:
        logger.warning(f"No user folders found in {user_image_dir}")
        return
    
    logger.info(f"Found {len(user_folders)} user folders to process")
    
    # Initialize model (done once for all users)
    model, colors = setup_model(weights_path, conf_threshold, iou_threshold)
    
    # Process images for each user
    total_processed = 0
    total_images = 0
    
    for user_folder in user_folders:
        user_id = os.path.basename(user_folder)
        logger.info(f"Processing images for user: {user_id}")
        
        processed, total = process_user_folder(
            user_folder, 
            model, 
            colors,
            conf_threshold,
            iou_threshold
        )
        
        total_processed += processed
        total_images += total
        
        logger.info(f"Completed user {user_id}: {processed}/{total} images processed")
    
    # Log summary
    elapsed_time = time.time() - start_time
    logger.info(f"Processing complete: {total_processed}/{total_images} images processed across {len(user_folders)} users")
    logger.info(f"Total execution time: {elapsed_time:.2f} seconds")

def main():
    # Parse command line arguments
    parser = argparse.ArgumentParser(description='Process images for all users in the User_Image directory')
    
    parser.add_argument('--user-dir', '-u', 
                        default=str(Path(__file__).parent / "User_Image"),
                        help='Path to User_Image directory')
    
    parser.add_argument('--weights', '-w', 
                        help='Path to model weights file (default: weights/best-yolov8n.pt)')
    
    parser.add_argument('--conf', '-c', type=float, default=0.25, 
                        help='Confidence threshold (default: 0.25)')
    
    parser.add_argument('--iou', type=float, default=0.5, 
                        help='IOU threshold (default: 0.5)')
    
    args = parser.parse_args()
    
    # Process images for all users
    process_all_users(
        user_image_dir=args.user_dir,
        weights_path=args.weights,
        conf_threshold=args.conf,
        iou_threshold=args.iou
    )

if __name__ == "__main__":
    main()