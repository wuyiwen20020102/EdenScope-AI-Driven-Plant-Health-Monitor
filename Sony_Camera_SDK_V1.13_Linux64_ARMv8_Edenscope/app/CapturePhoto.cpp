#include <cstdlib>
#include <thread>
#if defined(USE_EXPERIMENTAL_FS)
#include <experimental/filesystem>
namespace fs = std::experimental::filesystem;
#else
#include <filesystem>
namespace fs = std::filesystem;
#if defined(__APPLE__)
#include <unistd.h>
#endif
#endif

#include <cstdint>
#include <iomanip>
#include "CRSDK/CameraRemote_SDK.h"
#include "CameraDevice.h"
#include "Text.h"

//#define LIVEVIEW_ENB

#define MSEARCH_ENB

namespace SDK = SCRSDK;

int main()
{
    // Change global locale to native locale
    std::locale::global(std::locale(""));

    // Make the stream's locale the same as the current global locale
    cli::tin.imbue(std::locale());
    cli::tout.imbue(std::locale());

    cli::tout << "RemoteSampleApp v1.13.00 running...\n\n";

    CrInt32u version = SDK::GetSDKVersion();
    int major = (version & 0xFF000000) >> 24;
    int minor = (version & 0x00FF0000) >> 16;
    int patch = (version & 0x0000FF00) >> 8;
    // int reserved = (version & 0x000000FF);

    cli::tout << "Remote SDK version: ";
    cli::tout << major << "." << minor << "." << std::setfill(TEXT('0')) << std::setw(2) << patch << "\n";
    cli::tout << "Initialize Remote SDK...\n";
    cli::tout << "Working directory: " << fs::current_path() << '\n';

    auto init_success = SDK::Init();
    if (!init_success) {
        cli::tout << "Failed to initialize Remote SDK. Terminating.\n";
        SDK::Release();
        std::exit(EXIT_FAILURE);
    }
    cli::tout << "Remote SDK successfully initialized.\n\n";

#ifdef MSEARCH_ENB
    cli::tout << "Enumerate connected camera devices...\n";
    SDK::ICrEnumCameraObjectInfo* camera_list = nullptr;
    auto enum_status = SDK::EnumCameraObjects(&camera_list);
    if (CR_FAILED(enum_status) || camera_list == nullptr) {
        cli::tout << "No cameras detected. Connect a camera and retry.\n";
        SDK::Release();
        std::exit(EXIT_FAILURE);
    }
    auto ncams = camera_list->GetCount();
    cli::tout << "Camera enumeration successful. " << ncams << " detected.\n\n";

    for (CrInt32u i = 0; i < ncams; ++i) {
        auto camera_info = camera_list->GetCameraObjectInfo(i);
        cli::text conn_type(camera_info->GetConnectionTypeName());
        cli::text model(camera_info->GetModel());
        cli::text id = TEXT("");
        if (TEXT("IP") == conn_type) {
            cli::NetworkInfo ni = cli::parse_ip_info(camera_info->GetId(), camera_info->GetIdSize());
            id = ni.mac_address;
        }
        else id = ((TCHAR*)camera_info->GetId());
        cli::tout << '[' << i + 1 << "] " << model.data() << " (" << id.data() << ")\n";
    }

    if (ncams == 0) {
        cli::tout << "No cameras detected. Connect a camera and retry.\n";
        SDK::Release();
        std::exit(EXIT_FAILURE);
    } else {
        cli::tout << "Automatically selecting the first detected camera.\n";
    }

    CrInt32u no = 1;

    typedef std::shared_ptr<cli::CameraDevice> CameraDevicePtr;
    typedef std::vector<CameraDevicePtr> CameraDeviceList;
    CameraDeviceList cameraList; // all
    std::int32_t cameraNumUniq = 1;
    std::int32_t selectCamera = 1;

    cli::tout << "Connect to selected camera...\n";
    auto* camera_info = camera_list->GetCameraObjectInfo(no - 1);

    cli::tout << "Create camera SDK camera callback object.\n";
    CameraDevicePtr camera = CameraDevicePtr(new cli::CameraDevice(cameraNumUniq, camera_info));
    cameraList.push_back(camera); // add 1st

    cli::tout << "Release enumerated camera list.\n";
    camera_list->Release();
#endif

    // Connect to the camera in Remote Control Mode
    if (camera->is_connected()) {
        cli::tout << "Camera is already connected.\n";
    }
    else {
        camera->connect(SDK::CrSdkControlMode_Remote, SDK::CrReconnecting_ON);
        while (!camera->is_connected())
        {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }

    if ((SDK::CrSdkControlMode_Remote == camera->get_sdkmode())){
        cli::tout << TEXT("Remote Control Mode Connected\n");

        // Verify that the camera is connected
        if (camera->is_connected()) {
            // Capture an image
            camera->af_shutter();
            while (!camera->is_image_downloaded)
            {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
        } else {
            cli::tout << "Failed to connect to the camera.\n";
            // Handle the error or exit
            SDK::Release();
            std::exit(EXIT_FAILURE);
        }
    }

    // Disconnect and clean up
    if (camera->is_connected()) {
        auto disconnect_status = camera->disconnect();
        if (!disconnect_status) {
            // Try again
            disconnect_status = camera->disconnect();
        }
    }
    camera->release();

    // Release SDK resources
    SDK::Release();

    return 0;
}