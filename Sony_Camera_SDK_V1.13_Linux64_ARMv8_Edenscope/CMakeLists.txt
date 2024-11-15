cmake_minimum_required(VERSION 3.10)


project(CapturePhoto LANGUAGES CXX)


# Append project cmake script dir
list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)


# Enumerate project files
include(enum_cli_hdr)
include(enum_cli_src)
include(enum_crsdk_hdr)


# Define output target
set(Edenscope_Capture_Image "${PROJECT_NAME}")
add_executable(${Edenscope_Capture_Image}
    ${cli_hdrs}
    ${cli_srcs}
    ${crsdk_hdrs}
)


# Set target properties
set_target_properties(${Edenscope_Capture_Image} PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED YES
    CXX_EXTENSIONS NO
    BUILD_RPATH "$ORIGIN"
    INSTALL_RPATH "$ORIGIN"
)


# Specify char is signed-char to fix mismatch with Raspbian
target_compile_options(${Edenscope_Capture_Image} PUBLIC -fstack-protector-all PRIVATE -fsigned-char)


# Include directories
target_include_directories(${Edenscope_Capture_Image} PRIVATE
        ${crsdk_hdr_dir} # Defined in enum_crsdk_hdr.cmake
        ${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/include
)


# Configure external library directories
set(ldir ${CMAKE_CURRENT_SOURCE_DIR}/external)
set(cr_ldir ${ldir}/crsdk)


# Link Camera Remote SDK library
find_library(camera_remote Cr_Core HINTS ${cr_ldir})
target_link_libraries(${Edenscope_Capture_Image} PRIVATE
        ${camera_remote}
)


# Handle GCC versions
if("${CMAKE_CXX_COMPILER_ID}" STREQUAL "GNU")
    if(CMAKE_CXX_COMPILER_VERSION VERSION_LESS 8)
        # Must use std::experimental namespace if older than GCC8
        message("[${PROJECT_NAME}] GCC version less than 8. Using std::experimental namespace.")
        target_compile_definitions(${Edenscope_Capture_Image} PRIVATE USE_EXPERIMENTAL_FS)
    endif()
    if(CMAKE_CXX_COMPILER_VERSION VERSION_LESS 9)
        # Must explicitly link separate std::filesystem if older than GCC9
        message("[${PROJECT_NAME}] GCC version less than 9. Explicitly linking separate std::filesystem library.")
        target_link_libraries(${Edenscope_Capture_Image} PRIVATE stdc++fs)
    endif()
endif()


target_link_libraries(${Edenscope_Capture_Image} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/Linux/libopencv_core.so.408
    ${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/Linux/libopencv_highgui.so.408
    ${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/Linux/libopencv_imgcodecs.so.408
    ${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/Linux/libopencv_imgproc.so.408
)


add_custom_command(TARGET ${Edenscope_Capture_Image} PRE_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory ${cr_ldir} $<TARGET_FILE_DIR:${Edenscope_Capture_Image}>
)
   
add_custom_command(TARGET ${Edenscope_Capture_Image} PRE_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/Linux/libopencv_core.so.408" $<TARGET_FILE_DIR:${Edenscope_Capture_Image}>
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/Linux/libopencv_highgui.so.408" $<TARGET_FILE_DIR:${Edenscope_Capture_Image}>
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/Linux/libopencv_imgcodecs.so.408" $<TARGET_FILE_DIR:${Edenscope_Capture_Image}>
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/external/opencv/Linux/libopencv_imgproc.so.408" $<TARGET_FILE_DIR:${Edenscope_Capture_Image}>
)


# Install application
install(TARGETS ${Edenscope_Capture_Image} DESTINATION .)
install(DIRECTORY ${cr_ldir}/ DESTINATION .)