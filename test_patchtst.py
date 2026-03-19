import torch
from AI.modules.signal.models.PatchTST.architecture import PatchTST_Model

model = PatchTST_Model()
x = torch.randn(2, 120, 17)
out = model(x)
print('입력:', x.shape)
print('출력:', out.shape)
print('성공!')