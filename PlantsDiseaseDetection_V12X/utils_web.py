import os

import cv2
import pandas as pd
import streamlit as st
from PIL import Image
from QtFusion.path import abs_path


def save_uploaded_file(uploaded_file):
    """
    Save the uploaded file to the server.

    Args:
        uploaded_file (UploadedFile): The file uploaded via Streamlit.

    Returns:
        str: The full path where the file is saved. Returns None if no file is uploaded.

    When a user uploads a file, this function saves it to the specified directory on the server.
    """
    # Check if a file was uploaded
    if uploaded_file is not None:
        base_path = "tempDir"  # Define the base path for saving the file

        # If the directory does not exist, create it
        if not os.path.exists(base_path):
            os.makedirs(base_path)

        # Get the complete file path
        file_path = os.path.join(base_path, uploaded_file.name)

        # Open the file in binary write mode
        with open(file_path, "wb") as f:
            f.write(uploaded_file.getbuffer())  # Write the file

        return file_path  # Return the file path

    return None  # Return None if no file is uploaded


def concat_results(result, location, confidence, time_taken):
    """
    Concatenate and display detection results.

    Args:
        result (str): Detection result.
        location (str): Detection location.
        confidence (str): Confidence level.
        time_taken (str): Time taken for detection.

    Returns:
        DataFrame: A DataFrame containing the detection result details.
    """
    # Create a DataFrame containing the provided information
    result_data = {
        "Detection Result": [result],
        "Location": [location],
        "Confidence": [confidence],
        "Time Taken": [time_taken]
    }

    results_df = pd.DataFrame(result_data)
    return results_df


def load_default_image():
    """
    Load the default image.

    Returns:
        Image: The default image object.
    """
    ini_image = abs_path("icon/ini-image.png")
    return Image.open(ini_image)


def get_camera_names():
    """
    Get the list of available camera names.

    Returns:
        list: A list containing "Camera Disabled" and available camera indices.
    """
    camera_names = ["Camera Disabled", "0"]
    max_test_cameras = 3  # Define the maximum number of cameras to test; adjust as needed

    for i in range(max_test_cameras):
        cap = cv2.VideoCapture(i, cv2.CAP_DSHOW)
        if cap.isOpened() and str(i) not in camera_names:
            camera_names.append(str(i))
            cap.release()
    if len(camera_names) == 1:
        st.write("No available camera found")
    return camera_names
