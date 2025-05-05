import random
import tempfile
import time
import uuid  # for generating unique filenames
import cv2
import numpy as np
import streamlit as st
import requests
import json

from QtFusion.path import abs_path
from QtFusion.utils import drawRectBox

from LoggerRes import ResultLogger, LogTable
from YOLOv12XModel import YOLOv12XDetector
from datasets.PlantsDisease.label_name import Label_list
from style_css import def_css_hitml
from utils_web import save_uploaded_file, concat_results, load_default_image, get_camera_names

from openai import OpenAI

# Instantiate the ModelScope client.
client = OpenAI(
    base_url='https://api-inference.modelscope.cn/v1/',
    api_key='1554ad09-f2ee-41db-9c26-8f3c128d7b75',  # ModelScope Token
)

# -------------------------------------------------------------------------
# Deep Diagnosis using QVQ model.
def get_deep_diagnosis_from_image(image_url: str) -> str:
    response = client.chat.completions.create(
        model='Qwen/QVQ-72B-Preview',  # QVQ model for diagnosis
        messages=[{
            'role': 'user',
            'content': [
                {
                    'type': 'text',
                    'text': 'Please diagnose this leaf image and list the top 3 possible diseases with a brief explanation.'
                },
                {
                    'type': 'image_url',
                    'image_url': {'url': image_url},
                },
            ],
        }],
        stream=True,
        max_tokens=15000,
    )
    result_text = ""
    for chunk in response:
        if (chunk.choices and hasattr(chunk.choices[0].delta, 'content')
            and chunk.choices[0].delta.content):
            result_text += chunk.choices[0].delta.content
    return result_text

# -------------------------------------------------------------------------
# Deep Scan using Qwen2.5-VL model.
def get_deep_scan_from_image(image_url: str) -> dict:
    response = client.chat.completions.create(
        model='Qwen/Qwen2.5-VL-72B-Instruct',  # Qwen2.5-VL model for spatial detection
        messages=[{
            'role': 'user',
            'content': [
                {
                    'type': 'text',
                    'text': (
                        "Please perform a deep scan of this image to detect all disease spots on the leaf. "
                        "Return the results in JSON format with the following structure: "
                        '{"detections": [{"label": "<disease type>", "confidence": <confidence>, "box": [x1, y1, x2, y2]}, ...]}'
                    )
                },
                {
                    'type': 'image_url',
                    'image_url': {'url': image_url},
                },
            ],
        }],
        stream=True,
        max_tokens=8192,
    )
    result_text = ""
    for chunk in response:
        if (chunk.choices and hasattr(chunk.choices[0].delta, 'content')
            and chunk.choices[0].delta.content):
            result_text += chunk.choices[0].delta.content
    try:
        result_json = json.loads(result_text)
        return result_json
    except Exception as e:
        return {"error": f"Failed to parse JSON: {str(e)}", "raw": result_text}

# -------------------------------------------------------------------------
# Main UI class.
class Detection_UI:
    def __init__(self):
        self.cls_name = Label_list
        self.colors = [[random.randint(0, 255) for _ in range(3)] for _ in range(len(self.cls_name))]
        self.title = "EdenScope Leaf Detection Portal"
        self.setup_page()
        def_css_hitml()

        self.model_type = None
        self.conf_threshold = 0.25
        self.iou_threshold = 0.5

        self.selected_camera = None
        self.file_type = None
        self.uploaded_file = None
        self.uploaded_video = None
        self.image_url = ""  # For image link input
        self.custom_model_file = None

        self.detection_result = None
        self.detection_location = None
        self.detection_confidence = None
        self.detection_time = None

        self.display_mode = None
        self.close_flag = None
        self.close_placeholder = None
        self.image_placeholder = None
        self.image_placeholder_res = None
        self.table_placeholder = None
        self.log_table_placeholder = None
        self.selectbox_placeholder = None
        self.selectbox_target = None
        self.progress_bar = None

        self.saved_log_data = abs_path("tempDir/log_table_data.csv", path_type="current")

        if 'logTable' not in st.session_state:
            st.session_state['logTable'] = LogTable(self.saved_log_data)
        if 'available_cameras' not in st.session_state:
            st.session_state['available_cameras'] = get_camera_names()
        self.available_cameras = st.session_state['available_cameras']
        self.logTable = st.session_state['logTable']

        if 'model' not in st.session_state:
            st.session_state['model'] = YOLOv12XDetector()
        self.model = st.session_state['model']
        self.model.load_model(model_path=abs_path("weights_v12/best.pt", path_type="current"))
        self.colors = [[random.randint(0, 255) for _ in range(3)] for _ in range(len(self.model.names))]
        self.setup_sidebar()

    def setup_page(self):
        st.set_page_config(
            page_title=self.title,
            page_icon="🚀",
            initial_sidebar_state="expanded"
        )

    def setup_sidebar(self):
        st.sidebar.header("Model")
        self.model_type = st.sidebar.selectbox("Choose Model", ["YOLOv8/v5", "others"])
        model_file_option = st.sidebar.radio("Model File", ["Default", "Self-defined"])
        if model_file_option == "Self-defined":
            model_file = st.sidebar.file_uploader("Choose .pt File", type="pt")
            if model_file is not None:
                self.custom_model_file = save_uploaded_file(model_file)
                self.model.load_model(model_path=self.custom_model_file)
                self.colors = [[random.randint(0, 255) for _ in range(3)] for _ in range(len(self.model.names))]
        elif model_file_option == "Default":
            self.model.load_model(model_path=abs_path("weights_v12/best.pt", path_type="current"))
            self.colors = [[random.randint(0, 255) for _ in range(3)] for _ in range(len(self.model.names))]

        self.conf_threshold = float(st.sidebar.slider("Confidence Threshold", 0.0, 1.0, 0.25))
        self.iou_threshold = float(st.sidebar.slider("IOU Threshold", 0.0, 1.0, 0.5))

        st.sidebar.header("Camera Configuration")
        self.selected_camera = st.sidebar.selectbox("Select Camera", self.available_cameras)

        st.sidebar.header("Recognition Settings")
        # Include "Image Link" option as in the previous code.
        self.file_type = st.sidebar.selectbox("Select File Type", ["Image File", "Video File", "Image Link"])
        if self.file_type == "Image File":
            self.uploaded_file = st.sidebar.file_uploader("Upload Image", type=["jpg", "png", "jpeg"])
        elif self.file_type == "Video File":
            self.uploaded_video = st.sidebar.file_uploader("Upload Video File", type=["mp4"])
        elif self.file_type == "Image Link":
            self.image_url = st.sidebar.text_input("Image URL:", "")

        if self.selected_camera == "Camera Disabled":
            if self.file_type == "Image File":
                st.sidebar.write("Please select an image and click 'Start' to perform image detection!")
            if self.file_type == "Video File":
                st.sidebar.write("Please select a video and click 'Start' to perform video detection!")
            if self.file_type == "Image Link":
                st.sidebar.write("Please paste an image link and click 'Start' to perform image detection!")
        else:
            st.sidebar.write("Please click 'Start' to activate camera detection!")

    def load_model_file(self):
        if self.custom_model_file:
            self.model.load_model(self.custom_model_file)

    def handle_image_file(self):
        """
        Reads the uploaded image file and runs inference.
        """
        file_bytes = np.frombuffer(self.uploaded_file.read(), np.uint8)
        image_cv2 = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
        if image_cv2 is None:
            st.error("Failed to decode uploaded image.")
            return
        image, detInfo, select_info = self.frame_process(image_cv2, self.uploaded_file.name)
        self.display_results(image_cv2, image, detInfo, select_info, self.uploaded_file.name)

    def handle_image_link(self):
        """
        Fetches an image from the user-provided HTTP/S link and runs inference.
        """
        try:
            response = requests.get(self.image_url, stream=True)
            response.raise_for_status()  # raise if invalid
            file_bytes = np.asarray(bytearray(response.content), dtype=np.uint8)
            image_cv2 = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
            if image_cv2 is None:
                st.error("Failed to decode the image from the provided link.")
                return
            image, detInfo, select_info = self.frame_process(image_cv2, self.image_url)
            self.display_results(image_cv2, image, detInfo, select_info, self.image_url)
        except requests.exceptions.RequestException as e:
            st.error(f"Error downloading image from URL: {str(e)}")

    def display_results(self, original_image, processed_image, detInfo, select_info, src_name):
        """
        Displays the original and processed images, updates logs, and refreshes the UI.
        """
        self.logTable.clear_frames()
        self.progress_bar.progress(0)
        self.logTable.add_frames(processed_image, detInfo, cv2.resize(original_image, (640, 640)))
        self.selectbox_placeholder = st.empty()
        self.selectbox_target = self.selectbox_placeholder.selectbox("Filter Targets", select_info, key="22113")
        new_width = 1080
        new_height = int(new_width * (9 / 16))
        resized_image = cv2.resize(processed_image, (new_width, new_height))
        resized_frame = cv2.resize(original_image, (new_width, new_height))
        if self.display_mode == "Single View":
            self.image_placeholder.image(resized_image, channels="BGR", caption="Detection Result")
        else:
            self.image_placeholder.image(resized_frame, channels="BGR", caption="Original View")
            self.image_placeholder_res.image(resized_image, channels="BGR", caption="Detection View")
        self.logTable.save_to_csv()
        self.logTable.update_table(self.log_table_placeholder)
        self.progress_bar.progress(100)

    def process_camera_or_file(self):
        if self.selected_camera != "Camera Disabled":
            self.logTable.clear_frames()
            self.close_flag = self.close_placeholder.button(label="Stop")
            cap = cv2.VideoCapture(int(self.selected_camera))
            total_frames = 1000
            current_frame = 0
            self.progress_bar.progress(0)
            while cap.isOpened() and not self.close_flag:
                ret, frame = cap.read()
                if ret:
                    image, detInfo, _ = self.frame_process(frame, "Camera: " + self.selected_camera)
                    new_width = 1080
                    new_height = int(new_width * (9 / 16))
                    resized_image = cv2.resize(image, (new_width, new_height))
                    resized_frame = cv2.resize(frame, (new_width, new_height))
                    if self.display_mode == "Single View":
                        self.image_placeholder.image(resized_image, channels="BGR", caption="Camera View")
                    else:
                        self.image_placeholder.image(resized_frame, channels="BGR", caption="Original View")
                        self.image_placeholder_res.image(resized_image, channels="BGR", caption="Detection View")
                    self.logTable.add_frames(image, detInfo, cv2.resize(frame, (640, 640)))
                    progress_percentage = int((current_frame / total_frames) * 100)
                    self.progress_bar.progress(progress_percentage)
                    current_frame = (current_frame + 1) % total_frames
                else:
                    st.error("Unable to capture image.")
                    break
            if self.close_flag:
                self.logTable.save_to_csv()
                self.logTable.update_table(self.log_table_placeholder)
                cap.release()
            self.logTable.save_to_csv()
            self.logTable.update_table(self.log_table_placeholder)
            cap.release()
        else:
            # Process based on file type.
            if self.file_type == "Image File" and self.uploaded_file is not None:
                self.handle_image_file()
            elif self.file_type == "Video File" and self.uploaded_video is not None:
                self.logTable.clear_frames()
                self.close_flag = self.close_placeholder.button(label="Stop")
                video_file = self.uploaded_video
                tfile = tempfile.NamedTemporaryFile()
                tfile.write(video_file.read())
                cap = cv2.VideoCapture(tfile.name)
                total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
                fps = cap.get(cv2.CAP_PROP_FPS)
                total_length = total_frames / fps if fps > 0 else 0
                self.progress_bar.progress(0)
                current_frame = 0
                while cap.isOpened() and not self.close_flag:
                    ret, frame = cap.read()
                    if ret:
                        image, detInfo, _ = self.frame_process(frame, self.uploaded_video.name)
                        new_width = 1080
                        new_height = int(new_width * (9 / 16))
                        resized_image = cv2.resize(image, (new_width, new_height))
                        resized_frame = cv2.resize(frame, (new_width, new_height))
                        if self.display_mode == "Single View":
                            self.image_placeholder.image(resized_image, channels="BGR", caption="Video View")
                        else:
                            self.image_placeholder.image(resized_frame, channels="BGR", caption="Original View")
                            self.image_placeholder_res.image(resized_image, channels="BGR", caption="Detection View")
                        self.logTable.add_frames(image, detInfo, cv2.resize(frame, (640, 640)))
                        if total_length > 0:
                            progress_percentage = int(((current_frame + 1) / total_frames) * 100)
                            self.progress_bar.progress(progress_percentage)
                        current_frame += 1
                    else:
                        break
                if self.close_flag:
                    self.logTable.save_to_csv()
                    self.logTable.update_table(self.log_table_placeholder)
                    cap.release()
                self.logTable.save_to_csv()
                self.logTable.update_table(self.log_table_placeholder)
                cap.release()
            elif self.file_type == "Image Link" and self.image_url:
                self.handle_image_link()
            else:
                st.warning("Please select a camera or upload a file.")

    def toggle_comboBox(self, frame_id):
        if len(self.logTable.saved_results) > 0:
            frame = self.logTable.saved_images_ini[-1]
            image = frame
            for i, detInfo in enumerate(self.logTable.saved_results):
                if frame_id != -1 and frame_id != i:
                    continue
                if len(detInfo) > 0:
                    name, bbox, conf, use_time, cls_id = detInfo
                    label = '%s %.0f%%' % (name, conf * 100)
                    disp_res = ResultLogger()
                    res = disp_res.concat_results(name, bbox, str(round(conf, 2)), str(round(use_time, 2)))
                    self.table_placeholder.table(res)
                    if len(self.logTable.saved_images_ini) > 0:
                        if len(self.colors) < cls_id:
                            self.colors = [[random.randint(0, 255) for _ in range(3)] for _ in range(cls_id + 1)]
                        image = drawRectBox(image, bbox, alpha=0.2, addText=label, color=self.colors[cls_id])
            new_width = 1080
            new_height = int(new_width * (9 / 16))
            resized_image = cv2.resize(image, (new_width, new_height))
            resized_frame = cv2.resize(frame, (new_width, new_height))
            if self.display_mode == "Single View":
                self.image_placeholder.image(resized_image, channels="BGR", caption="Detection View")
            else:
                self.image_placeholder.image(resized_frame, channels="BGR", caption="Original View")
                self.image_placeholder_res.image(resized_image, channels="BGR", caption="Detection View")

    def frame_process(self, image, file_name):
        image = cv2.resize(image, (640, 640))
        pre_img = self.model.preprocess(image)
        params = {'conf': self.conf_threshold, 'iou': self.iou_threshold}
        self.model.set_param(params)
        t1 = time.time()
        pred = self.model.predict(pre_img)
        t2 = time.time()
        use_time = t2 - t1
        det = pred[0]
        detInfo = []
        select_info = ["All Targets"]
        if det is not None and len(det):
            det_info = self.model.postprocess(pred)
            if len(det_info):
                disp_res = ResultLogger()
                res = None
                cnt = 0
                for info in det_info:
                    name, bbox, conf, cls_id = info['class_name'], info['bbox'], info['score'], info['class_id']
                    label = '%s %.0f%%' % (name, conf * 100)
                    res = disp_res.concat_results(name, bbox, str(round(conf, 2)), str(round(use_time, 2)))
                    image = drawRectBox(image, bbox, alpha=0.2, addText=label, color=self.colors[cls_id])
                    self.logTable.add_log_entry(file_name, name, bbox, conf, use_time)
                    detInfo.append([name, bbox, conf, use_time, cls_id])
                    select_info.append(name + "-" + str(cnt))
                    cnt += 1
                self.table_placeholder.table(res)
        return image, detInfo, select_info

    def frame_table_process(self, frame, caption):
        self.image_placeholder.image(frame, channels="BGR", caption=caption)
        detection_result = "None"
        detection_location = "[0, 0, 0, 0]"
        detection_confidence = str(random.random())
        detection_time = "0.00s"
        res = concat_results(detection_result, detection_location, detection_confidence, detection_time)
        self.table_placeholder.table(res)
        cv2.waitKey(1)

    def setupMainWindow(self):
        st.title(self.title)
        st.write("--------")
        st.write("by EdenScope team. Ver.01")
        st.write("--------")
        col1, col2, col3, col4, col5 = st.columns([2, 2, 1, 2, 1])
        with col1:
            self.display_mode = st.radio("Display Mode", ["Single View", "Dual View"])
        if self.display_mode == "Single View":
            self.image_placeholder = st.empty()
            if not self.logTable.saved_images_ini:
                self.image_placeholder.image(load_default_image(), caption="Original View")
        else:
            self.image_placeholder = st.empty()
            self.image_placeholder_res = st.empty()
            if not self.logTable.saved_images_ini:
                self.image_placeholder.image(load_default_image(), caption="Original View")
                self.image_placeholder_res.image(load_default_image(), caption="Detection View")
        self.progress_bar = st.progress(0)
        res = concat_results("None", "[0, 0, 0, 0]", "0.00", "0.00s")
        self.table_placeholder = st.empty()
        self.table_placeholder.table(res)
        st.write("---------------------")
        if st.button("Export Results"):
            self.logTable.save_to_csv()
            res = self.logTable.save_frames_file()
            st.write("🚀 Detection result file saved: " + self.saved_log_data)
            if res:
                st.write(f"🚀 Video/Image file saved: {res}")
            self.logTable.clear_data()

        # --- Deep Diagnosis Button using QVQ ---
        if st.button("Deep Diagnosis"):
            # Automatically use the same image URL as used for YOLO.
            if self.file_type == "Image Link" and self.image_url:
                deep_result = get_deep_diagnosis_from_image(self.image_url)
                st.markdown("### Deep Diagnosis Result")
                st.write(deep_result)
            else:
                st.warning("Deep Diagnosis requires file type 'Image Link' with a valid URL.")

        # --- Deep Scan Button using Qwen2.5-VL ---
        if st.button("Deep Scan"):
            if self.file_type == "Image Link" and self.image_url:
                deep_scan_result = get_deep_scan_from_image(self.image_url)
                st.markdown("### Deep Scan Result")
                st.write(deep_scan_result)
                if "detections" in deep_scan_result:
                    current_image = self.logTable.saved_images_ini[-1]
                    for det in deep_scan_result["detections"]:
                        label = det.get("label", "Unknown")
                        confidence = det.get("confidence", 0)
                        box = det.get("box", [0, 0, 0, 0])
                        current_image = drawRectBox(
                            current_image, box,
                            addText=f"{label} {confidence*100:.0f}%",
                            alpha=0.2, color=[random.randint(0, 255) for _ in range(3)]
                        )
                    self.image_placeholder.image(current_image, channels="BGR", caption="Deep Scan Result")
                else:
                    st.error("Deep Scan did not return detections in the expected format.")
            else:
                st.warning("Deep Scan requires file type 'Image Link' with a valid URL.")

        self.log_table_placeholder = st.empty()
        self.logTable.update_table(self.log_table_placeholder)
        with col5:
            st.write("")
            self.close_placeholder = st.empty()
        with col2:
            self.selectbox_placeholder = st.empty()
            detected_targets = ["All Targets"]
            for i, info in enumerate(self.logTable.saved_results):
                name, bbox, conf, use_time, cls_id = info
                detected_targets.append(name + "-" + str(i))
            self.selectbox_target = self.selectbox_placeholder.selectbox("Filter Targets", detected_targets)
            for i, info in enumerate(self.logTable.saved_results):
                name, bbox, conf, use_time, cls_id = info
                if self.selectbox_target == name + "-" + str(i):
                    self.toggle_comboBox(i)
                elif self.selectbox_target == "All Targets":
                    self.toggle_comboBox(-1)
        with col4:
            st.write("")
            run_button = st.button("Start")
            if run_button:
                self.process_camera_or_file()
            else:
                if not self.logTable.saved_images_ini:
                    self.image_placeholder.image(load_default_image(), caption="Original View")
                    if self.display_mode == "Dual View":
                        self.image_placeholder_res.image(load_default_image(), caption="Detection View")

# -------------------------------------------------------------------------
# Run the application.
if __name__ == "__main__":
    app = Detection_UI()
    app.setupMainWindow()
