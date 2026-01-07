# ChatApp - Lightweight LLM Chat Application

This app has been optimized to be lightweight by removing bundled model assets.

## Required Setup

Before using this app, you need to have the Genie model bundle on your device:

**Required location:** `/data/local/tmp/genie_bundle`

**Required files:**
- llama_v3_2_3b_instruct_part_1_of_3.bin
- llama_v3_2_3b_instruct_part_2_of_3.bin
- llama_v3_2_3b_instruct_part_3_of_3.bin
- genie_config.json
- htp_backend_ext_config.json
- tokenizer.json
- libGenie.so
- libQnnHtp.so
- libQnnHtpPrepare.so
- libQnnSystem.so
- libQnnSaver.so
- libQnnHtpV75Stub.so
- libQnnHtpV75CalculatorStub.so
- libQnnHtpV75Skel.so
- genie-t2t-run (executable)

## Setup Instructions

Use adb to push the files to your device:

```bash
adb push genie_bundle /data/local/tmp/
adb shell "chmod -R 777 /data/local/tmp/genie_bundle"
```

## Supported Devices

- Snapdragon 8 Gen 2 (QCS8550)
- Snapdragon 8 Gen 3 (SM8650)
- Snapdragon 8 Elite (SM8750)

## App Size

This lightweight version is <5MB compared to the original >2.5GB bundled version.

All model files are loaded from the device storage at runtime.

