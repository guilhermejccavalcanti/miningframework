#!/usr/bin/env python3

"""
Script to run MergeGen batch inference.

This script loads the MergeGen model (by importing the MergeInference class)
and applies it to each entry in a standard JSON file (which must be a list
of records). It uses pandas to read the JSON file.

Each generated resolution is saved to a separate text file in an
output directory, named sequentially (0.txt, 1.txt, ...).

Usage example:
python3 run_batch_inference.py --input_file data/my_input.json --output_dir results/
"""

import argparse
import json
import logging
from pathlib import Path
import sys
import pandas as pd

# Import the class from the other file
try:
    from mergegen_inference import MergeInference
except ImportError:
    print("Error: Could not find 'mergegen_inference.py'.", file=sys.stderr)
    print("Make sure both scripts are in the same directory.", file=sys.stderr)
    sys.exit(1)

# Try to import tqdm, but continue if not installed
try:
    from tqdm import tqdm
except ImportError:
    print("Warning: tqdm not found. Progress bar will not be shown.", file=sys.stderr)
    print("Install with: pip install tqdm", file=sys.stderr)
    # Create a dummy
    def tqdm(iterable, *args, **kwargs):
        return iterable

# --- Logging Configuration ---
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] - %(message)s",
    handlers=[
        logging.FileHandler("inference.log"),
        logging.StreamHandler(sys.stdout)
    ]
)


def main():
    """
    Main function to run the batch inference.
    """
    parser = argparse.ArgumentParser(
        description="Run MergeGen inference on a standard JSON file (list of records)."
    )
    parser.add_argument(
        "--input_file",
        type=str,
        default="data/mergegen_input_after_cutoff.json",
        help="Path to the input .json file (must be a list of records)."
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        required=True,
        help="Directory to save the output .txt files."
    )
    parser.add_argument(
        "--model_path",
        type=str,
        default="best_model.pt",
        help="Path to the trained model (.pt) file."
    )
    args = parser.parse_args()

    # --- 1. Validate paths ---
    input_path = Path(args.input_file)
    output_path = Path(args.output_dir)
    model_path = Path(args.model_path)

    if not input_path.exists():
        logging.error(f"Input file not found: {input_path}")
        sys.exit(1)
        
    if not model_path.exists():
        logging.error(f"Model file not found: {model_path}")
        logging.error("Please download the model or provide the correct path via --model_path.")
        sys.exit(1)

    output_path.mkdir(parents=True, exist_ok=True)

    # --- 2. Load the Model (Once) ---
    logging.info(f"Loading MergeGen model from {model_path}...")
    try:
        inference_tool = MergeInference(model_path=str(model_path))
    except Exception as e:
        logging.error(f"Failed to load model: {e}")
        logging.exception("Details:")
        sys.exit(1)
    logging.info("Model loaded successfully.")

    # --- 3. Process the input file ---
    logging.info(f"Processing input file: {input_path}")

    # --- Load the JSON file using pandas ---
    try:
        df = pd.read_json(input_path)
        
        # Fill any missing (NaN) string values with empty strings
        df['base'] = df['base'].fillna("")
        df['v1'] = df['v1'].fillna("")
        df['v2'] = df['v2'].fillna("")
        
        num_records = len(df)
        logging.info(f"Found {num_records} records to process.")

    except ValueError as e:
        logging.error(f"Failed to parse JSON file {input_path} with pandas: {e}")
        logging.error("Check if the file is a valid JSON list of records or a column-oriented JSON.")
        sys.exit(1)
    except KeyError as e:
        logging.error(f"Error: Input JSON is missing a required column: {e}")
        sys.exit(1)
    except Exception as e:
        logging.error(f"Failed to read input file {input_path}: {e}")
        sys.exit(1)

    # Main inference loop
    try:
        # Iterate over the DataFrame rows
        for row in tqdm(df.itertuples(), total=num_records, desc="Running inference"):
            i = row.Index  # Get the row index (0, 1, 2, ...)
            try:
                # b. Extract the code strings from the row
                # Assumes row.base, row.v1, and row.v2 are already strings
                base_code = row.base
                v1_code = row.v1
                v2_code = row.v2
                chunk_id = row.chunk_id
                generated_resolution = inference_tool.generate_resolution(
                    base_code, v1_code, v2_code
                )
                
                output_file_name = f"{chunk_id}.txt"
                output_file_path = output_path / output_file_name
                
                with open(output_file_path, 'w', encoding='utf-8') as out_f:
                    out_f.write(generated_resolution)
                    
            except Exception as e:
                # This will catch the 'AttributeError' if the data is not a string
                logging.error(f"Error processing record {i}: {e}")
                commit_hash = getattr(row, "commit_hash", "N/A")
                logging.exception(f"Data that caused error (record {i}): {commit_hash}")

    except KeyboardInterrupt:
        logging.info("\nInference interrupted by user.")
    except Exception as e:
        logging.error(f"A critical error occurred during the batch loop: {e}")
        
    logging.info(f"Inference complete. Results saved to {output_path}")


if __name__ == '__main__':
    main()