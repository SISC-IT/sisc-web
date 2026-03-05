# AI/modules/signal/models/PatchTST/train.py
import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
import os
from torch.utils.data import DataLoader, TensorDataset
from .architecture import PatchTST_Model

# 설정값
CONFIG = {
    'seq_len': 120,
    'input_features': 7,
    'batch_size': 32,
    'learning_rate': 0.0001,
    'epochs': 100,
    'patience': 10,
    'model_save_path': 'AI/data/weights/PatchTST_best.pt'
}

def train_model(train_loader, val_loader, device):
    """PatchTST 모델 학습 파이프라인"""
    
    # 모델 초기화
    model = PatchTST_Model(
        seq_len=CONFIG['seq_len'],
        enc_in=CONFIG['input_features']
    ).to(device)

    # 손실함수 & 옵티마이저
    # 이진 분류(상승/하락) 문제이므로 BCEWithLogitsLoss 사용
    criterion = nn.BCEWithLogitsLoss()
    optimizer = optim.AdamW(model.parameters(), lr=CONFIG['learning_rate'])
    
    best_val_loss = float('inf')
    patience_counter = 0
    
    print(f"🚀 PatchTST 학습 시작 (Device: {device})")

    for epoch in range(CONFIG['epochs']):
        # --- Training ---
        model.train()
        train_loss = 0
        for X_batch, y_batch in train_loader:
            X_batch, y_batch = X_batch.to(device), y_batch.to(device)
            
            optimizer.zero_grad()
            output = model(X_batch)
            loss = criterion(output, y_batch.view(-1, 1))
            
            loss.backward()
            optimizer.step()
            train_loss += loss.item()
            
        avg_train_loss = train_loss / len(train_loader)

        # --- Validation ---
        model.eval()
        val_loss = 0
        with torch.no_grad():
            for X_val, y_val in val_loader:
                X_val, y_val = X_val.to(device), y_val.to(device)
                output = model(X_val)
                loss = criterion(output, y_val.view(-1, 1))
                val_loss += loss.item()
        
        avg_val_loss = val_loss / len(val_loader)
        
        print(f"Epoch [{epoch+1}/{CONFIG['epochs']}] Train Loss: {avg_train_loss:.4f} | Val Loss: {avg_val_loss:.4f}")

        # --- Early Stopping & Save ---
        if avg_val_loss < best_val_loss:
            best_val_loss = avg_val_loss
            patience_counter = 0
            # 저장 경로 디렉토리 생성
            os.makedirs(os.path.dirname(CONFIG['model_save_path']), exist_ok=True)
            torch.save(model.state_dict(), CONFIG['model_save_path'])
            print(f"✅ 모델 저장됨: {CONFIG['model_save_path']}")
        else:
            patience_counter += 1
            if patience_counter >= CONFIG['patience']:
                print("🛑 Early Stopping 발동")
                break
                
    return model

def run_training(X_train, y_train, X_val, y_val):
    """
    외부에서 호출 가능한 학습 진입점
    X: [Samples, Seq_Len, Features] numpy array
    y: [Samples] numpy array (0 or 1)
    """
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    
    # Tensor 변환
    train_data = TensorDataset(torch.FloatTensor(X_train), torch.FloatTensor(y_train))
    val_data = TensorDataset(torch.FloatTensor(X_val), torch.FloatTensor(y_val))
    
    train_loader = DataLoader(train_data, batch_size=CONFIG['batch_size'], shuffle=True)
    val_loader = DataLoader(val_data, batch_size=CONFIG['batch_size'], shuffle=False)
    
    trained_model = train_model(train_loader, val_loader, device)
    return trained_model