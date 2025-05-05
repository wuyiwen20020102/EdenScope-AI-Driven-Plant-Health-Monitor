import win32serviceutil
import win32service
import win32event
import servicemanager
import time
import boto3
import cv2
import numpy as np

class S3ProcessingService(win32serviceutil.ServiceFramework):
    _svc_name_ = "S3ProcessingService"
    _svc_display_name_ = "S3 Processing Service"
    
    def __init__(self, args):
        win32serviceutil.ServiceFramework.__init__(self, args)
        self.hWaitStop = win32event.CreateEvent(None, 0, 0, None)
        self.running = True

    def SvcStop(self):
        self.ReportServiceStatus(win32service.SERVICE_STOP_PENDING)
        self.running = False
        win32event.SetEvent(self.hWaitStop)

    def SvcDoRun(self):
        servicemanager.LogMsg(servicemanager.EVENTLOG_INFORMATION_TYPE,
                              servicemanager.PYS_SERVICE_STARTED,
                              (self._svc_name_, ''))
        self.main()

    def main(self):
        # Create S3 client
        s3 = boto3.client('s3')
        bucket = "your-bucket-name"
        processed_prefix = "processed/"
        upload_prefix = "uploads/"
        
        while self.running:
            # Poll S3 for new images in the upload_prefix folder
            response = s3.list_objects_v2(Bucket=bucket, Prefix=upload_prefix)
            if 'Contents' in response:
                for obj in response['Contents']:
                    key = obj['Key']
                    # Download the image
                    s3_response = s3.get_object(Bucket=bucket, Key=key)
                    image_bytes = s3_response['Body'].read()
                    np_img = np.frombuffer(image_bytes, np.uint8)
                    img = cv2.imdecode(np_img, cv2.IMREAD_COLOR)
                    
                    # Process the image (e.g., detect leaves and draw red boxes)
                    processed_img = process_image(img)  # Define your function
                    
                    # Encode processed image and upload back to S3
                    ret, buffer = cv2.imencode('.jpg', processed_img)
                    if ret:
                        new_key = key.replace(upload_prefix, processed_prefix, 1)
                        s3.put_object(Bucket=bucket, Key=new_key, Body=buffer.tobytes(), ContentType="image/jpeg")
                        # Optionally delete the original upload
                        s3.delete_object(Bucket=bucket, Key=key)
            time.sleep(10)  # Sleep for a bit before polling again

def process_image(img):
    # Your processing logic here. For example, draw a red rectangle.
    # This is just an example. Replace with your actual model inference.
    height, width = img.shape[:2]
    cv2.rectangle(img, (int(width*0.1), int(height*0.1)), (int(width*0.9), int(height*0.9)), (0,0,255), 3)
    return img

if __name__ == '__main__':
    win32serviceutil.HandleCommandLine(S3ProcessingService)