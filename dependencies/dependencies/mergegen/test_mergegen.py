from mergegen_inference import MergeInference
import pandas as pd
import time
import os

resolution_path = f"../sbcr_genetic/mergegen_candidates"
times_path = f"../sbcr_genetic/mergegen_times"
os.makedirs(resolution_path, exist_ok=True)
os.makedirs(times_path, exist_ok=True)

def write_resolution(chunk_id, resolution):
    with open(f"{resolution_path}/{chunk_id}", 'w') as f:
        f.write(resolution)

def write_time(chunk_id, time):
    with open(f"{times_path}/{chunk_id}", 'w') as f:
        f.write(str(time))

if __name__ == '__main__':
    try:
        inference_tool = MergeInference(model_path="best_model.pt")

        chunks = pd.read_json('../sbcr_genetic/data/dataset2_Java_testing.json')
        chunks['chunk_id'] = chunks['merge_id'].astype(str) + '-' + chunks['chunk_number'].astype(str)
        for index, row in chunks.iterrows():
            chunk_id = row['chunk_id']
            print(f"{time.ctime()} -- Resolving chunk {chunk_id} ({index}/{len(chunks)})")
            v1 = row['all_raw_a']
            v2 = row['all_raw_b']
            base = row['all_raw_base']
            start_time = time.time()
            generated_resolution = inference_tool.generate_resolution(base, v1, v2)
            elapsed_time = time.time() - start_time
            write_resolution(chunk_id, generated_resolution)
            write_time(chunk_id, elapsed_time)

    except FileNotFoundError:
        print("\nErro: O arquivo 'best_model.pt' não foi encontrado.")
        print("Por favor, certifique-se de que o modelo treinado está no mesmo diretório ou forneça o caminho correto.")
    except Exception as e:
        print(f"\nOcorreu um erro inesperado: {e}")