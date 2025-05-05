#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Helper script to process test images using the batch processor.
"""

from batch_process_images import batch_process
from QtFusion.path import abs_path

if __name__ == "__main__":
    # Process all images in the test_media directory
    input_dir = abs_path("test_media", path_type="current")
    
    # Use default output directory (test_media/Processed_Image)
    batch_process(
        input_dir=input_dir,
        weights_path=abs_path("weights/best-yolov8n.pt", path_type="current"),
        conf_threshold=0.25,
        iou_threshold=0.5
    )