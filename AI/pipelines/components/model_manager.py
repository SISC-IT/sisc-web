# AI/pipelines/components/model_manager.py

import os
import shutil
import traceback
from AI.modules.signal.models import get_model
from AI.modules.signal.core.data_loader import DataLoader

# 프로젝트 루트 경로 동적 확보 (가중치 파일 탐색용)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))

def initialize_models(loader: DataLoader, strategy_config: dict, feature_columns: list, active_models: list) -> dict:
    """
    [AI 모델 초기화 담당]
    지정된 활성 모델(active_models) 리스트를 순회하며 동적으로 객체를 초기화하고 가중치를 로드합니다.
    
    Args:
        loader (DataLoader): 데이터 로더 인스턴스 (종목 및 섹터 ID 참조용)
        strategy_config (dict): 시퀀스 길이 등 전략 설정값
        feature_columns (list): 모델 입력에 사용될 피처 리스트
        active_models (list): 로드할 모델 이름들의 리스트 (예: ['transformer', 'patchtst'])
        
    Returns:
        dict: 초기화가 완료된 모델 래퍼(Wrapper) 객체들의 딕셔너리
    """
    model_wrappers = {}
    
    # 데이터 로더에서 실제 사용 가능한 종목 및 섹터의 개수를 추출
    real_n_tickers = len(loader.ticker_to_id)
    real_n_sectors = len(loader.sector_to_id)

    print(f"2. 모델 초기화 중... 대상 모델: {active_models}")
    
    for model_name in active_models:
        # 모델 빌드를 위한 공통 설정 딕셔너리 구성
        config = {
            "seq_len": strategy_config['seq_len'],
            "features": feature_columns,
            "n_tickers": real_n_tickers,
            "n_sectors": real_n_sectors
        }
        
        try:
            # 1) Factory 패턴을 이용한 동적 모델 객체 생성 및 빌드
            wrapper = get_model(model_name, config)
            wrapper.build(input_shape=(strategy_config['seq_len'], len(feature_columns)))
            
            # 2) 모델별 가중치 및 스케일러 경로 동적 생성
            weights_dir = os.path.join(project_root, f"AI/data/weights/{model_name}")
            weights_path = os.path.join(weights_dir, "tests/multi_horizon_model_test.keras")
            scaler_path = os.path.join(weights_dir, "tests/multi_horizon_scaler_test.pkl")
            
            # 3) 가중치 파일 로딩 로직 (오류 발생 시 HDF5 Fallback 지원)
            if os.path.exists(weights_path):
                try:
                    wrapper.model.load_weights(weights_path)
                    print(f"✅ [{model_name.upper()}] 모델 가중치 로드 완료 (Standard)")
                except Exception as load_e:
                    # Keras/Zip 포맷 오류 발생 시 _temp_fallback.h5 형식으로 임시 복사 후 로딩 시도
                    if "not a zip file" in str(load_e) or "header" in str(load_e):
                        temp_h5_path = weights_path.replace(".keras", "_temp_fallback.h5")
                        try:
                            shutil.copyfile(weights_path, temp_h5_path)
                            wrapper.model.load_weights(temp_h5_path)
                            print(f"✅ [{model_name.upper()}] 모델 가중치 로드 완료 (HDF5 Fallback)")
                        except Exception as e_h5:
                            print(f"❌ [{model_name.upper()}] HDF5 로드 실패: {e_h5}")
                            raise e_h5
                        finally:
                            if os.path.exists(temp_h5_path):
                                os.remove(temp_h5_path)
                    else:
                        raise load_e
                
                # 4) 스케일러 파일 로딩
                if os.path.exists(scaler_path):
                    wrapper.load_scaler(scaler_path)
                else:
                    print(f"⚠️ [{model_name.upper()}] 스케일러 파일이 없습니다. 예측 시 오작동할 수 있습니다.")

                # 정상 로드된 래퍼 객체를 딕셔너리에 보관
                model_wrappers[f"{model_name}_v1"] = wrapper
            else:
                print(f"⚠️ [{model_name.upper()}] 모델 가중치 파일 없음 ({weights_path}). 앙상블 시 제외되거나 중립 점수로 처리됩니다.")
                
        except Exception as e:
            print(f"❌ [{model_name.upper()}] 초기화 실패: {e}")
            traceback.print_exc()

    return model_wrappers