# AI/pipelines/components/model_manager.py

import os
import shutil
import traceback
from typing import Dict, List, Any

# 내부 모듈 임포트 (경로 및 구현은 프로젝트 구조에 따름)
from AI.modules.signal.models import get_model
from AI.modules.signal.core.data_loader import DataLoader

# ==============================================================================
# 1. 전역 경로 설정
# ==============================================================================
# 프로젝트 루트 경로 동적 확보 (가중치 파일 탐색용)
# 현재 파일(model_manager.py)의 위치를 기준으로 3단계 상위 디렉토리를 프로젝트 루트로 설정합니다.
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))

def initialize_models(loader: DataLoader, strategy_config: Dict[str, Any], feature_columns: List[str], active_models: List[str]) -> Dict[str, Any]:
    """
    [AI 모델 초기화 담당]
    지정된 활성 모델(active_models) 리스트를 순회하며 동적으로 객체를 초기화하고 
    저장된 가중치(Weights) 및 스케일러(Scaler)를 로드합니다.
    
    Args:
        loader (DataLoader): 데이터 로더 인스턴스 (종목 및 섹터 ID 참조 등 메타데이터 활용)
        strategy_config (dict): 시퀀스 길이(seq_len) 등 모델 구성에 필요한 전략 설정값
        feature_columns (list): 모델 입력에 사용될 피처(특성) 리스트
        active_models (list): 로드할 모델 이름들의 리스트 (예: ['transformer', 'patchtst'])
        
    Returns:
        dict: 초기화 및 가중치 로드가 완료된 모델 래퍼(Wrapper) 객체들의 딕셔너리
              (키: "{model_name}_v1", 값: 모델 래퍼 인스턴스)
    """
    model_wrappers = {}
    
    # --------------------------------------------------------------------------
    # 2. 메타데이터 추출
    # --------------------------------------------------------------------------
    # 데이터 로더에서 실제 사용 가능한 종목(Ticker) 및 섹터의 개수를 추출합니다.
    # 이는 모델의 임베딩(Embedding) 레이어나 출력 레이어 크기를 동적으로 맞추기 위함입니다.
    real_n_tickers = len(loader.ticker_to_id)
    real_n_sectors = len(loader.sector_to_id)

    print(f"🔄 [ModelManager] 모델 초기화 진행 중... 대상 모델: {active_models}")
    
    # --------------------------------------------------------------------------
    # 3. 모델 동적 생성 및 가중치 로딩 루프
    # --------------------------------------------------------------------------
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
            # get_model 함수가 설정값(config)에 맞는 모델 래퍼 인스턴스를 반환한다고 가정합니다.
            wrapper = get_model(model_name, config)
            # 모델의 입력 형태(seq_len, 피처 개수)를 지정하여 내부 그래프를 빌드(초기화)합니다.
            wrapper.build(input_shape=(strategy_config['seq_len'], len(feature_columns)))
            
            # 2) 모델별 가중치 및 스케일러 경로 동적 생성
            # TODO: 향후 'tests' 폴더 하드코딩을 환경변수(운영/테스트)로 분리하는 것을 권장합니다.
            weights_dir = os.path.join(project_root, f"AI/data/weights/{model_name}")
            weights_path = os.path.join(weights_dir, "tests/multi_horizon_model_test.keras")
            scaler_path = os.path.join(weights_dir, "tests/multi_horizon_scaler_test.pkl")
            
            # 3) 가중치 파일 로딩 로직 (오류 발생 시 HDF5 Fallback 지원)
            if os.path.exists(weights_path):
                try:
                    # 기본적으로 .keras 형식의 가중치 로드를 시도합니다.
                    wrapper.model.load_weights(weights_path)
                    print(f"✅ [{model_name.upper()}] 모델 가중치 로드 완료 (Standard)")
                    
                except Exception as load_e:
                    # Keras/Zip 포맷 오류 발생 시 HDF5 형식 우회 로딩 시도 (Fallback 로직)
                    # 파일 손상이나 버전 차이로 인해 zip/header 에러가 발생할 때 사용됩니다.
                    if "not a zip file" in str(load_e) or "header" in str(load_e):
                        temp_h5_path = weights_path.replace(".keras", "_temp_fallback.h5")
                        try:
                            # 원본 파일을 복사하여 .h5 확장자로 변경 후 로딩 (Keras 하위 호환성 활용)
                            shutil.copyfile(weights_path, temp_h5_path)
                            wrapper.model.load_weights(temp_h5_path)
                            print(f"✅ [{model_name.upper()}] 모델 가중치 로드 완료 (HDF5 Fallback)")
                        except Exception as e_h5:
                            print(f"❌ [{model_name.upper()}] HDF5 폴백 로드 실패: {e_h5}")
                            raise e_h5
                        finally:
                            # 임시 파일이 남아있다면 반드시 삭제하여 스토리지 낭비 방지
                            if os.path.exists(temp_h5_path):
                                os.remove(temp_h5_path)
                    else:
                        # Zip/Header 오류가 아닌 다른 원인의 오류라면 그대로 예외를 발생시킵니다.
                        raise load_e
            else:
                # 가중치 파일이 존재하지 않을 경우, 시스템이 다운되지 않고 앙상블에서 제외되도록 경고만 출력합니다.
                print(f"⚠️ [{model_name.upper()}] 모델 가중치 파일 없음 ({weights_path}). 앙상블 시 제외되거나 중립 점수로 처리됩니다.")
                continue # 파일이 없으므로 스케일러 로드 등을 생략하고 다음 모델로 넘어갑니다.

            # 4) 스케일러 파일 로딩 (방어적 코드 적용)
            if os.path.exists(scaler_path):
                # 💡 덕 타이핑(Duck Typing): 래퍼 객체에 'load_scaler' 메서드가 구현되어 있는지 확인
                # PatchTST와 같이 내부 정규화(Revin 등)를 사용하여 외부 스케일러가 필요 없는 모델을 위한 안전장치입니다.
                if hasattr(wrapper, 'load_scaler') and callable(getattr(wrapper, 'load_scaler')):
                    wrapper.load_scaler(scaler_path)
                    print(f"✅ [{model_name.upper()}] 전용 스케일러 로드 완료")
                else:
                    print(f"ℹ️ [{model_name.upper()}] load_scaler 메서드가 필요 없는 모델입니다. (생략)")
            else:
                print(f"⚠️ [{model_name.upper()}] 스케일러 파일이 없습니다. 피처 스케일링이 누락되어 모델 성능이 저하될 수 있습니다.")

            # 5) 정상 로드된 래퍼 객체를 딕셔너리에 보관 (접미사 _v1 부여)
            model_wrappers[f"{model_name}_v1"] = wrapper
            
        except Exception as e:
            # 특정 모델 초기화에 실패하더라도 전체 프로세스가 종료되지 않도록 예외 처리 후 로깅
            print(f"❌ [{model_name.upper()}] 초기화 전체 실패: {e}")
            traceback.print_exc()

    return model_wrappers