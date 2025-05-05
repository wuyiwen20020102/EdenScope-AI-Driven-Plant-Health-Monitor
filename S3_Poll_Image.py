import os
import time
import boto3
import logging
import sys
from pathlib import Path
import subprocess
import glob
import mimetypes

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
        logging.FileHandler('s3_poll_image.log')
    ]
)

BUCKET_NAME = "edenplanthealthappbucket58d3e-dev"
PROTECTED_PREFIX = "protected/"
PROCESSED_PREFIX = "processed/"  # Prefix for processed images
LOCAL_BASE_FOLDER = r"C:\Users\wuyiw\OneDrive\Desktop\EdenScope AI-Driven Plant Health Monitoring Payload\User_Image"

POLL_INTERVAL = 5  # seconds

def get_s3_keys(s3):
    """Return a set of all S3 keys (excluding directories) under PROTECTED_PREFIX."""
    s3_keys = set()
    response = s3.list_objects_v2(Bucket=BUCKET_NAME, Prefix=PROTECTED_PREFIX)
    if "Contents" in response:
        for obj in response["Contents"]:
            key = obj["Key"]
            if key.endswith("/") or key == PROTECTED_PREFIX:
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
        # Expect key format: protected/<userFolder>/<filename>
        parts = key.split('/')
        if len(parts) < 3:
            continue
        # Replace colon with underscore in the user folder name
        user_folder_name = parts[1].replace(":", "_")
        filename = parts[2]

        local_user_folder = os.path.join(LOCAL_BASE_FOLDER, user_folder_name)
        if not os.path.exists(local_user_folder):
            os.makedirs(local_user_folder)
            logging.info(f"Created local folder: {local_user_folder}")

        local_path = os.path.join(local_user_folder, filename)
        if os.path.exists(local_path):
            logging.info(f"File already exists locally: {local_path}, skipping.")
            continue

        try:
            logging.info(f"Downloading s3://{BUCKET_NAME}/{key} to {local_path}")
            s3.download_file(BUCKET_NAME, key, local_path)
            new_files_downloaded = True
        except Exception as e:
            logging.error(f"Error downloading {key}: {e}")
    
    return new_files_downloaded

def delete_local_files(s3_keys):
    """
    Delete any local files that are not present in the s3_keys.
    
    For original images, delete them if they're not in S3.
    For processed images, delete them if their corresponding original image doesn't exist in S3.
    
    Returns:
        list: List of deleted original image paths
    """
    deleted_originals = []
    
    # First pass: Delete original images not in S3 and track them
    for root, dirs, files in os.walk(LOCAL_BASE_FOLDER):
        # Skip Processed_Image directories in this pass
        if "Processed_Image" in root.split(os.sep):
            continue
            
        for filename in files:
            local_path = os.path.join(root, filename)
            
            # Skip files in Processed_Image folders (redundant check)
            if os.sep + "Processed_Image" + os.sep in local_path or local_path.endswith(os.sep + "Processed_Image"):
                continue
                
            # Determine the relative path from LOCAL_BASE_FOLDER.
            relative_path = os.path.relpath(local_path, LOCAL_BASE_FOLDER)
            
            # Reconstruct what the S3 key should be.
            s3_relative = relative_path.replace(os.sep, "/")
            s3_key_candidate = PROTECTED_PREFIX + s3_relative.replace("_", ":", 1)  # only replace the first underscore

            if s3_key_candidate not in s3_keys:
                try:
                    logging.info(f"Local file {local_path} not found in S3; deleting.")
                    os.remove(local_path)
                    deleted_originals.append(local_path)
                except Exception as e:
                    logging.error(f"Error deleting {local_path}: {e}")
    
    # Second pass: Delete processed images whose original images were deleted
    for root, dirs, files in os.walk(LOCAL_BASE_FOLDER):
        # Only process Processed_Image directories in this pass
        if "Processed_Image" not in root.split(os.sep):
            continue
            
        for filename in files:
            # Skip files that aren't processed images
            if not (filename.endswith("_processed.jpg") or filename.endswith("_processed.jpeg") or 
                    filename.endswith("_processed.png") or "_processed." in filename):
                continue
                
            local_path = os.path.join(root, filename)
            
            # Determine the corresponding original image name and path
            original_filename = filename.replace("_processed", "")
            
            # Get the user folder from the current path
            path_parts = root.split(os.sep)
            processed_folder_index = path_parts.index("Processed_Image")
            if processed_folder_index > 0:
                user_folder = path_parts[processed_folder_index-1]
                original_path = os.path.join(LOCAL_BASE_FOLDER, user_folder, original_filename)
                
                # If the original was deleted or doesn't exist, delete the processed image too
                if original_path in deleted_originals or not os.path.exists(original_path):
                    try:
                        logging.info(f"Deleting processed image {local_path} because original was deleted.")
                        os.remove(local_path)
                    except Exception as e:
                        logging.error(f"Error deleting processed image {local_path}: {e}")
    
    return deleted_originals

def upload_processed_images_to_s3(s3):
    """
    Upload processed images to S3 so they can be accessed by the Android app.
    """
    try:
        logging.info("Uploading processed images to S3...")
        
        # Get list of user folders
        user_folders = [d for d in os.listdir(LOCAL_BASE_FOLDER) 
                       if os.path.isdir(os.path.join(LOCAL_BASE_FOLDER, d))]
        
        upload_count = 0
        
        for user_folder in user_folders:
            # Path to user's processed images folder
            processed_folder = os.path.join(LOCAL_BASE_FOLDER, user_folder, "Processed_Image")
            
            # Skip if folder doesn't exist
            if not os.path.exists(processed_folder):
                continue
                
            # Replace underscore with colon in user folder name for S3 key
            s3_user_folder = user_folder.replace("_", ":", 1)
            
            # Get all processed images
            processed_images = glob.glob(os.path.join(processed_folder, "*_processed*"))
            
            for img_path in processed_images:
                # Get just the filename
                filename = os.path.basename(img_path)
                
                # Create the S3 key - we'll upload to a different prefix to avoid conflicts
                s3_key = f"{PROCESSED_PREFIX}{s3_user_folder}/{filename}"
                
                mime_type, _ = mimetypes.guess_type(img_path)
                if not mime_type:
                    # Fallback if Python can’t guess it
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
                        "ContentType": 'image/jpeg',
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

def process_images(s3):
    """Process all images in user folders using the plant disease detection model."""
    try:
        logging.info("Starting plant disease detection processing on all user images...")
        
        # Process all user images
        process_all_users(
            user_image_dir=LOCAL_BASE_FOLDER,
            weights_path=None,  # Use default weights
            conf_threshold=0.25,
            iou_threshold=0.5
        )
        
        logging.info("Plant disease detection processing complete.")
        
        # Upload processed images to S3
        upload_processed_images_to_s3(s3)
        
    except Exception as e:
        logging.error(f"Error during image processing: {e}")

def get_processed_s3_keys(s3):
    """Return a set of all processed image S3 keys under PROCESSED_PREFIX."""
    processed_keys = set()
    response = s3.list_objects_v2(Bucket=BUCKET_NAME, Prefix=PROCESSED_PREFIX)
    if "Contents" in response:
        for obj in response["Contents"]:
            key = obj["Key"]
            if key.endswith("/") or key == PROCESSED_PREFIX:
                continue
            processed_keys.add(key)
    return processed_keys

def delete_processed_s3_files(s3, processed_s3_keys, original_s3_keys):
    """Delete processed S3 files whose original images no longer exist."""
    deleted_count = 0
    
    for processed_key in list(processed_s3_keys):
        try:
            # Extract user folder and filename
            parts = processed_key.split('/')
            if len(parts) < 3:
                continue
                
            # Get user folder and filename
            user_folder = parts[1]
            filename = parts[2]
            
            # Determine original filename (remove _processed suffix)
            original_filename = filename.replace("_processed", "")
            if "." in original_filename:
                original_filename = original_filename[:original_filename.rindex(".")] + os.path.splitext(filename)[1]
            
            # Construct what the original file key would be
            original_key = f"{PROTECTED_PREFIX}{user_folder}/{original_filename}"
            
            # If original doesn't exist, delete the processed file
            if original_key not in original_s3_keys:
                logging.info(f"Original file for {processed_key} no longer exists, deleting processed file from S3")
                s3.delete_object(Bucket=BUCKET_NAME, Key=processed_key)
                deleted_count += 1
                
        except Exception as e:
            logging.error(f"Error checking/deleting processed S3 file {processed_key}: {e}")
    
    return deleted_count

def main():
    s3 = boto3.client("s3")
    
    # Flag to track if new files were downloaded in the current poll
    new_files_downloaded = False
    # Flag to track if any files were deleted
    files_deleted = False

    while True:
        try:
            # Get original images from S3
            logging.info(f"Listing objects in s3://{BUCKET_NAME}/{PROTECTED_PREFIX}")
            original_s3_keys = get_s3_keys(s3)
            
            # Get processed images from S3
            logging.info(f"Listing processed objects in s3://{BUCKET_NAME}/{PROCESSED_PREFIX}")
            processed_s3_keys = get_processed_s3_keys(s3)
            
            # Download new or updated original files
            new_files = download_new_files(s3, original_s3_keys)
            if new_files:
                new_files_downloaded = True

            # Delete local files that no longer exist in S3
            deleted_files = delete_local_files(original_s3_keys)
            if deleted_files:
                files_deleted = True
                
            # Delete processed S3 files whose originals no longer exist
            delete_processed_s3_files(s3, processed_s3_keys, original_s3_keys)

            # Process images if new files were downloaded or if files were deleted
            if new_files_downloaded or files_deleted:
                process_images(s3)
                new_files_downloaded = False  # Reset the flags
                files_deleted = False

            logging.info("Sync and processing complete. Sleeping before next poll...")
        except Exception as e:
            logging.error(f"Error during S3 sync: {e}")

        time.sleep(POLL_INTERVAL)

if __name__ == "__main__":
    main()
