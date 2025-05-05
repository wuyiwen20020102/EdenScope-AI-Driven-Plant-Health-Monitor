#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Batch image processor for plant disease detection.
Processes all images in a directory, adds bounding boxes for detected diseases,
and saves the results to a 'Processed_Image' folder.
"""

import os
import cv2
import time
import argparse
import torch
from pathlib import Path
from QtFusion.path import abs_path
from QtFusion.utils import cv_imread, drawRectBox
from YOLOv8v5Model import YOLOv8v5Detector, ini_params
from datasets.PlantsDisease.label_name import Label_list

# Configure the model
def setup_model(weights_path=None, conf_threshold=0.25, iou_threshold=0.5):
    """
    Initialize and configure the YOLOv8v5 model.
    
    Args:
        weights_path (str): Path to model weights file
        conf_threshold (float): Confidence threshold for detections
        iou_threshold (float): IOU threshold for NMS
        
    Returns:
        tuple: (model, colors) - configured model and color map
    """
    # Initialize model
    model = YOLOv8v5Detector()
    
    # Load model weights
    if weights_path is None:
        weights_path = abs_path("weights/best-yolov8n.pt", path_type="current")
    
    model.load_model(weights_path)
    
    # Set detection parameters
    params = {'conf': conf_threshold, 'iou': iou_threshold}
    model.set_param(params)
    
    # Generate random colors for each class
    import random
    colors = [[random.randint(0, 255) for _ in range(3)] for _ in range(len(model.names))]
    
    return model, colors

def process_image(image_path, model, colors, save_dir):
    """
    Process a single image for plant disease detection.
    
    Args:
        image_path (str): Path to the image file
        model (YOLOv8v5Detector): The detection model
        colors (list): Color map for different classes
        save_dir (str): Directory to save the processed image
        
    Returns:
        bool: True if image was processed, False if error occurred
    """
    try:
        # Get original filename (without extension) for naming the processed image
        img_name = os.path.basename(image_path)
        img_name_no_ext = os.path.splitext(img_name)[0]
        output_path = os.path.join(save_dir, f"{img_name_no_ext}_processed{os.path.splitext(image_path)[1]}")
        
        # Skip if already processed
        if os.path.exists(output_path):
            print(f"Skipping {img_name} - already processed")
            return False
        
        # Read the image
        image = cv_imread(image_path)
        if image is None:
            print(f"Warning: Could not read image {image_path}")
            return False
        
        # Resize image for model
        image = cv2.resize(image, (640, 640))
        
        # Process image with model
        pre_img = model.preprocess(image)
        
        # Perform prediction
        t1 = time.time()
        pred = model.predict(pre_img)
        t2 = time.time()
        use_time = t2 - t1
        
        # Get detection results
        det = pred[0]
        has_detections = False
        
        # Process detections if any
        if det is not None and len(det):
            det_info = model.postprocess(pred)
            for info in det_info:
                # Get detection details
                name, bbox, conf, cls_id = info['class_name'], info['bbox'], info['score'], info['class_id']
                label = f'{name} {conf*100:.0f}%'
                
                # Draw bounding box
                image = drawRectBox(image, bbox, alpha=0.2, addText=label, color=colors[cls_id])
                has_detections = True
        
        # Save the processed image
        cv2.imwrite(output_path, image)
        
        # Print status
        if has_detections:
            print(f"Processed {img_name} - Found {len(det_info)} detections in {use_time:.2f}s")
        else:
            print(f"Processed {img_name} - No detections in {use_time:.2f}s")
        
        return True
        
    except Exception as e:
        print(f"Error processing {image_path}: {str(e)}")
        return False

def batch_process(input_dir, output_dir=None, weights_path=None, 
                  conf_threshold=0.25, iou_threshold=0.5):
    """
    Process all images in a directory, adding bounding boxes to detected plants diseases.
    
    Args:
        input_dir (str): Directory containing images to process
        output_dir (str): Directory to save processed images (default: input_dir/Processed_Image)
        weights_path (str): Path to model weights file
        conf_threshold (float): Confidence threshold for detections
        iou_threshold (float): IOU threshold for NMS
    """
    # Validate input directory
    if not os.path.isdir(input_dir):
        print(f"Error: Input directory '{input_dir}' does not exist")
        return

    # Setup output directory
    if output_dir is None:
        output_dir = os.path.join(input_dir, "Processed_Image")
    
    # Create output directory if it doesn't exist
    os.makedirs(output_dir, exist_ok=True)
    
    # Initialize model
    model, colors = setup_model(weights_path, conf_threshold, iou_threshold)
    
    # Get list of image files
    image_extensions = ['.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff']
    image_files = []
    
    for file in os.listdir(input_dir):
        file_path = os.path.join(input_dir, file)
        if os.path.isfile(file_path) and any(file.lower().endswith(ext) for ext in image_extensions):
            image_files.append(file_path)
    
    # Check if images were found
    if not image_files:
        print(f"No image files found in {input_dir}")
        return
    
    print(f"Found {len(image_files)} images to process")
    
    # Process each image
    processed_count = 0
    for image_path in image_files:
        if process_image(image_path, model, colors, output_dir):
            processed_count += 1
    
    print(f"Processing complete: {processed_count} images processed")

def main():
    # Parse command line arguments
    parser = argparse.ArgumentParser(description='Batch process images for plant disease detection')
    parser.add_argument('--input', '-i', required=True, help='Directory containing images to process')
    parser.add_argument('--output', '-o', help='Directory to save processed images (default: input_dir/Processed_Image)')
    parser.add_argument('--weights', '-w', help='Path to model weights file (default: weights/best-yolov8n.pt)')
    parser.add_argument('--conf', '-c', type=float, default=0.25, help='Confidence threshold (default: 0.25)')
    parser.add_argument('--iou', type=float, default=0.5, help='IOU threshold (default: 0.5)')
    
    args = parser.parse_args()
    
    # Process images
    batch_process(
        input_dir=args.input,
        output_dir=args.output,
        weights_path=args.weights,
        conf_threshold=args.conf,
        iou_threshold=args.iou
    )

if __name__ == "__main__":
    main()