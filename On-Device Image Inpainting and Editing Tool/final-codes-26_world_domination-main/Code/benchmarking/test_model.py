import tensorflow as tf
import numpy as np

def inspect_tflite_model(model_path):
    try:
        # Load the TFLite model and allocate tensors.
        interpreter = tf.lite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors()

        # Get input and output details
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        print(f"\n=== Model: {model_path} ===\n")

        # --- Print Input Details ---
        print(f"Number of Inputs: {len(input_details)}")
        for i, detail in enumerate(input_details):
            print(f"\n[Input {i}]")
            print(f"  Name: {detail['name']}")
            print(f"  Shape: {detail['shape']}  (Batch Size, Height, Width, Channels)")
            print(f"  Type: {detail['dtype']}")
            print(f"  Index: {detail['index']}")
            
            # Check for quantization (important for input preprocessing)
            scale, zero_point = detail['quantization']
            if scale > 0:
                print(f"  Quantization: Scale={scale}, Zero Point={zero_point}")
                print("  (NOTE: You must quantize your input data using these values!)")
            else:
                print("  Quantization: None (Standard floating point input)")

        # --- Print Output Details ---
        print(f"\n{'-'*30}")
        print(f"Number of Outputs: {len(output_details)}")
        for i, detail in enumerate(output_details):
            print(f"\n[Output {i}]")
            print(f"  Name: {detail['name']}")
            print(f"  Shape: {detail['shape']}")
            print(f"  Type: {detail['dtype']}")
            print(f"  Index: {detail['index']}")
            
            scale, zero_point = detail['quantization']
            if scale > 0:
                print(f"  Quantization: Scale={scale}, Zero Point={zero_point}")

    except Exception as e:
        print(f"Error loading model: {e}")

# REPLACE THIS with your actual .tflite file path
model_path = "./models/aotgan.tflite" 
inspect_tflite_model(model_path)
