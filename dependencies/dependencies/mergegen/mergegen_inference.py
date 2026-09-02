import os
import subprocess
import torch
import shutil
import tempfile
import uuid
from transformers import RobertaTokenizer, T5ForConditionalGeneration
from typing import List
from torch import nn
import warnings
import argparse
import sys

warnings.filterwarnings("ignore")

class MergeInference:
    """
    Encapsula o modelo MergeGen para realizar inferência em um único conflito de merge.
    Carrega um modelo pré-treinado e processa as versões 'base', 'a' e 'b' de um conflito
    para gerar uma resolução.
    """
    def __init__(self, model_path: str = "best_model.pt"):
        """
        Inicializa o tokenizer e o modelo pré-treinado.

        Args:
            model_path (str): O caminho para o arquivo do modelo treinado (.pt).
        """
        # 1. Configurações do Modelo e Tokenizer
        self.model_type = './codet5-small-local'
        # self.model_type = './codet5-base'
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        print(f"Usando dispositivo: {self.device}")

        # 2. Inicializar Tokenizer com os tokens especiais do MergeGen
        self.tokenizer = RobertaTokenizer.from_pretrained(self.model_type)
        brackets_tokens = ['<lbra>', '<mbra>', '<rbra>']
        self.tokenizer.add_tokens(brackets_tokens)
        
        # 3. Criar uma classe wrapper idêntica à do treinamento para carregar os pesos
        class MergeT5(nn.Module):
            def __init__(self, model_type, tokenizer_instance):
                super(MergeT5, self).__init__()
                self.t5 = T5ForConditionalGeneration.from_pretrained(model_type)
                self.t5.resize_token_embeddings(len(tokenizer_instance))

        # 4. Instanciar o modelo wrapper e carregar o state_dict nele
        wrapper_model = MergeT5(self.model_type, self.tokenizer)
        wrapper_model.load_state_dict(torch.load(model_path, map_location=self.device))
        
        # 5. Extrair o modelo T5 de dentro do wrapper para a inferência
        self.model = wrapper_model.t5
        self.model.to(self.device)
        self.model.eval()

        # Configurações de inferência, conforme o repositório original
        self.max_conflict_length = 500
        self.max_resolve_length = 200
        self.num_beams = 3
        
        # Tokens estruturais para representar o conflito
        self.lbra_token = '<lbra>'
        self.rbra_token = '<rbra>'

    def _git_merge_single(self, tokens_base: List[str], tokens_a: List[str], tokens_b: List[str]) -> List[str]:
        """
        Simula 'git merge-file --diff3' para criar a representação estrutural do conflito.
        Este método replica fielmente o processo descrito no artigo e implementado em `dataset_parallel.py`.
        """
        # Cria um diretório temporário para evitar conflitos de arquivos e garantir a limpeza.
        temp_dir = os.path.join(tempfile.gettempdir(), f'mergegen-inference-{uuid.uuid4()}')
        os.makedirs(temp_dir, exist_ok=True)

        try:
            # Escreve os tokens em arquivos, um por linha, para o git merge-file
            with open(os.path.join(temp_dir, 'base'), 'w', encoding='utf-8') as f:
                f.write('\n'.join(tokens_base))
            with open(os.path.join(temp_dir, 'a'), 'w', encoding='utf-8') as f:
                f.write('\n'.join(tokens_a))
            with open(os.path.join(temp_dir, 'b'), 'w', encoding='utf-8') as f:
                f.write('\n'.join(tokens_b))
            
            # Executa o comando git merge-file para obter a visão com marcadores de conflito
            merge_file_path = os.path.join(temp_dir, 'merge')
            cmd = f'git merge-file -L a -L base -L b {os.path.join(temp_dir, "a")} {os.path.join(temp_dir, "base")} {os.path.join(temp_dir, "b")} --diff3 -p > {merge_file_path}'
            subprocess.run(cmd, shell=True, check=False)
            
            # Processa o resultado do merge para criar a representação final
            with open(merge_file_path, 'r', encoding='utf-8') as f:
                merge_res = [x.strip() for x in f.readlines() if x.strip()]

            format_ids = [k for k, x in enumerate(merge_res) if x in ['<<<<<<< a', '>>>>>>> b', '||||||| base', '=======']]
            
            final_tokens = []
            start = 0
            for k in range(0, len(format_ids), 4):
                context_tokens = merge_res[start:format_ids[k]]
                a_tokens = merge_res[format_ids[k] + 1:format_ids[k + 1]]
                base_tokens = merge_res[format_ids[k + 1] + 1:format_ids[k + 2]]
                b_tokens = merge_res[format_ids[k + 2] + 1:format_ids[k + 3]]
                start = format_ids[k + 3] + 1

                final_tokens.extend(context_tokens)
                final_tokens.append(self.lbra_token)
                final_tokens.extend(a_tokens)
                final_tokens.append(self.tokenizer.sep_token)
                final_tokens.extend(base_tokens)
                final_tokens.append(self.tokenizer.sep_token)
                final_tokens.extend(b_tokens)
                final_tokens.append(self.rbra_token)

            if start < len(merge_res):
                final_tokens.extend(merge_res[start:])
            
            return [self.tokenizer.bos_token] + final_tokens + [self.tokenizer.eos_token]

        finally:
            # Garante que o diretório temporário seja removido
            shutil.rmtree(temp_dir, ignore_errors=True)
            
    def _pad_sequence(self, token_ids: List[int], max_length: int, pad_id: int) -> List[int]:
        """Aplica padding ou trunca a sequência de tokens para o tamanho máximo."""
        if len(token_ids) > max_length:
            return token_ids[:max_length]
        return token_ids + [pad_id] * (max_length - len(token_ids))

    def generate_resolution(self, base_code: str, version_a: str, version_b: str) -> str:
        """
        Gera a resolução de um conflito de merge a partir das três versões do código.

        Args:
            base_code (str): O código da versão ancestral comum.
            version_a (str): O código da primeira versão conflitante (ex: 'ours').
            version_b (str): O código da segunda versão conflitante (ex: 'theirs').

        Returns:
            str: O código com a resolução do conflito gerada pelo modelo.
        """
        # Etapa 1: Tokenizar as três versões de entrada
        base_tokens = self.tokenizer.tokenize(' '.join(base_code.split()))
        a_tokens = self.tokenizer.tokenize(' '.join(version_a.split()))
        b_tokens = self.tokenizer.tokenize(' '.join(version_b.split()))
        
        # Etapa 2: Criar a representação estrutural do conflito (mesmo processo do treino)
        conflict_tokens = self._git_merge_single(base_tokens, a_tokens, b_tokens)
        
        # Etapa 3: Converter para IDs e aplicar padding
        conflict_ids = self.tokenizer.convert_tokens_to_ids(conflict_tokens)
        padded_input = self._pad_sequence(conflict_ids, self.max_conflict_length, self.tokenizer.pad_token_id)
        
        # Etapa 4: Preparar tensores para o modelo
        input_tensor = torch.tensor([padded_input], device=self.device)
        attention_mask = (input_tensor != self.tokenizer.pad_token_id).long()
        
        # Etapa 5: Gerar a resolução usando o modelo (sem calcular gradientes)
        with torch.no_grad():
            output_ids = self.model.generate(
                input_ids=input_tensor,
                attention_mask=attention_mask,
                max_new_tokens=self.max_resolve_length,
                num_beams=self.num_beams,
                early_stopping=True
            )
        
        # Etapa 6: Decodificar os IDs gerados para texto
        resolution = self.tokenizer.decode(output_ids[0], skip_special_tokens=True)
        return resolution

def gerar_resolucao_de_conflito(left_conflict_path, base_conflict_path, right_conflict_path, resolution_path):
    """
    Recebe três arquivos de entrada (left, base, right) representando um conflito de merge, 
    gera a resolução e grava no arquivo de saída.
    Retorna 0 em sucesso, 1 em erro.
    """
    try:
        with open(left_conflict_path, 'r', encoding='utf-8') as fa:
            left_code = fa.read()
        with open(base_conflict_path, 'r', encoding='utf-8') as fb:
            base_code = fb.read()
        with open(right_conflict_path, 'r', encoding='utf-8') as fc:
            right_code = fc.read()

        inference_tool = MergeInference(model_path="best_model.pt")
        generated_resolution = inference_tool.generate_resolution(base_code, left_code, right_code)

        with open(resolution_path, 'w', encoding='utf-8') as fout:
            fout.write(generated_resolution)
        
        print("\nLeft:")
        print(left_code)
        print("\nBase:")
        print(base_code)
        print("\nLeft:")
        print(right_code)
        print("\nresolution:")
        print(generated_resolution)
        
        return 0
    except FileNotFoundError:
        print("\nErro: O arquivo 'best_model.pt' não foi encontrado.")
        print("Por favor, certifique-se de que o modelo treinado está no mesmo diretório ou forneça o caminho correto.")
        return 1
    except Exception as e:
        print(f"\nOcorreu um erro inesperado: {e}")
        return 1

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("left_conflict_path")
    parser.add_argument("base_conflict_path")
    parser.add_argument("right_conflict_path")
    parser.add_argument("resolution_path")
    args = parser.parse_args()

    rc = gerar_resolucao_de_conflito(
        args.left_conflict_path,
        args.base_conflict_path,
        args.right_conflict_path,
        args.resolution_path
    )
    sys.exit(0 if rc == 0 else 1)
