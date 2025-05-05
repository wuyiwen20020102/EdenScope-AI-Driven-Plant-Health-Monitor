#!/bin/bash
# EdenScope script to process all user images
# Add this to crontab to run periodically, e.g., every 10 minutes:
# */10 * * * * /path/to/process_all_users_images.sh

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Path to the parent EdenScope directory
EDENSCOPE_DIR="$(dirname "${SCRIPT_DIR}")"

# Activate Python environment if using venv (uncomment and modify if needed)
# source "${EDENSCOPE_DIR}/venv/bin/activate"

# Log file
LOG_FILE="${EDENSCOPE_DIR}/edenscope_batch_processing.log"

# Run the image processing script
echo "$(date): Starting EdenScope user image processing" >> "${LOG_FILE}"

# Check if a process is already running
if pgrep -f "python.*process_all_users_images.py" > /dev/null; then
    echo "$(date): Previous image processing job still running, exiting" >> "${LOG_FILE}"
    exit 0
fi

# Run the image processing script
cd "${EDENSCOPE_DIR}"
python "${EDENSCOPE_DIR}/process_all_users_images.py" >> "${LOG_FILE}" 2>&1

# Record completion
echo "$(date): Completed EdenScope user image processing" >> "${LOG_FILE}"
echo "----------------------------------------------" >> "${LOG_FILE}"