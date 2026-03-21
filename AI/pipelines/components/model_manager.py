# AI/pipelines/components/model_manager.py
import os
import shutil
import traceback
from typing import Dict, List, Any

# 내부 모듈 임포트
from AI.modules.signal.models import get_model
from AI.modules.signal.core.data_loader import DataLoader

# 프로젝트 루트 경로 동적 확보
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))

def initialize_models(loader: DataLoader, strategy_config: Dict[str, Any], feature_columns: List[str], active_models: List[str]) -> Dict[str, Any]:
    """
    [AI 모델 초기화 담당 - PyTorch & Keras 혼합 호환 버전]
    지정된 활성 모델을 순회하며 동적으로 객체를 초기화하고 가중치를 로드합니다.
    """
    model_wrappers = {}
    
    real_n_tickers = len(loader.ticker_to_id)
    real_n_sectors = len(loader.sector_to_id)

    print(f"🔄 [ModelManager] 모델 초기화 진행 중... 대상 모델: {active_models}")
    
    for model_name in active_models:
        config = {
            "seq_len": strategy_config['seq_len'],
            "features": feature_columns,
            "n_tickers": real_n_tickers,
            "n_sectors": real_n_sectors
        }
        
        try:
            # 1) 동적 모델 객체 생성 및 빌드
            wrapper = get_model(model_name, config)
            wrapper.build(input_shape=(strategy_config['seq_len'], len(feature_columns)))
            
            # 💡 [FIX 1] 프레임워크에 따른 가중치 파일 확장자 분기
            # Keras 기반인 transformer는 .keras, PyTorch 기반 모델들은 .pt(또는 .pth) 사용
            is_keras_model = (model_name.lower() == 'transformer')
            ext = ".keras" if is_keras_model else ".pt"
            
            weights_dir = os.path.join(project_root, f"AI/data/weights/{model_name}")
            weights_path = os.path.join(weights_dir, f"tests/multi_horizon_model_test{ext}")
            scaler_path = os.path.join(weights_dir, "tests/multi_horizon_scaler_test.pkl")
            
            # 2) 가중치 파일 로딩 로직
            if os.path.exists(weights_path):
                try:
                    # 💡 [FIX 2] 각 래퍼 클래스 내부에 구현된 다형성(Polymorphism)을 활용한 로드
                    # PyTorch 모델은 내부적으로 torch.load()를, Keras는 load_model()을 알아서 수행함
                    if hasattr(wrapper, 'load') and callable(getattr(wrapper, 'load')):
                        wrapper.load(weights_path)
                    else:
                        # 방어 코드: 혹시 load()가 구현 안 된 레거시 래퍼라면 기존 방식 사용
                        wrapper.model.load_weights(weights_path)
                        
                    print(f"✅ [{model_name.upper()}] 모델 가중치 로드 완료 (Standard)")
                    
                except Exception as load_e:
                    # 💡 [FIX 3] HDF5 Fallback은 Keras(Transformer) 전용이므로 분기 처리
                    if is_keras_model and ("not a zip file" in str(load_e) or "header" in str(load_e)):
                        temp_h5_path = weights_path.replace(".keras", "_temp_fallback.h5")
                        try:
                            shutil.copyfile(weights_path, temp_h5_path)
                            wrapper.model.load_weights(temp_h5_path) # Fallback 시에만 강제 로드
                            print(f"✅ [{model_name.upper()}] 모델 가중치 로드 완료 (HDF5 Fallback)")
                        except Exception as e_h5:
                            print(f"❌ [{model_name.upper()}] HDF5 폴백 로드 실패: {e_h5}")
                            raise e_h5
                        finally:
                            if os.path.exists(temp_h5_path):
                                os.remove(temp_h5_path)
                    else:
                        # PyTorch 모델에서 발생한 에러거나 Keras의 다른 에러인 경우 그대로 throw
                        raise load_e
            else:
                print(f"⚠️ [{model_name.upper()}] 모델 가중치 파일 없음 ({weights_path}). 앙상블 제외 처리됨.")
                continue

            # 3) 스케일러 파일 로딩
            if os.path.exists(scaler_path):
                if hasattr(wrapper, 'load_scaler') and callable(getattr(wrapper, 'load_scaler')):
                    wrapper.load_scaler(scaler_path)
                    print(f"✅ [{model_name.upper()}] 전용 스케일러 로드 완료")
                else:
                    print(f"ℹ️ [{model_name.upper()}] load_scaler 메서드가 불필요한 모델입니다. (생략)")
            else:
                print(f"⚠️ [{model_name.upper()}] 스케일러 파일이 없습니다.")

            # 정상 로드된 래퍼 객체를 딕셔너리에 보관
            model_wrappers[f"{model_name}_v1"] = wrapper
            
        except Exception as e:
            print(f"❌ [{model_name.upper()}] 초기화 실패: {e}")
            traceback.print_exc()

    return model_wrappers