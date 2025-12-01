# AI/xai/modules/prompt.py
from typing import List, Dict, Any
from .analysis import kdate, analyze_feature_series, consolidate_evidence
def build_short_prompt(decision: Dict[str, Any], evidence: List[Dict[str, Any]], context_data: List[Dict[str, Any]]) -> str:
    from .analysis import kdate, analyze_feature_series, consolidate_evidence

    ev_merged = consolidate_evidence(evidence)
    start_d, end_d = context_data[0]["Date"], context_data[-1]["Date"]

    # 간결 요약 라인 구성
    lines = []
    for ev in ev_merged:
        f = ev["feature_name"]
        t = analyze_feature_series(f, context_data)
        lines.append(
            f"**{f}** ({ev['contribution']*100:.1f}%): "
            f"{kdate(start_d)} {t['start']:.2f} → {kdate(end_d)} {t['end']:.2f}; "
            f"범위 {t['min']:.2f}~{t['max']:.2f}; "
            f"평균(초/중/후) {t['eavg']:.2f}/{t['mavg']:.2f}/{t['lavg']:.2f}"
        )
    trend_summary = "\n".join(lines)

    # 🌟 개선 프롬프트
    prompt = f"""
당신은 시계열 데이터의 구조적 패턴을 분석하는 **시니어 퀀트 애널리스트**입니다.  
리포트는 데이터 근거 중심이며, 불필요한 수식어 없이 간결하고 명확해야 합니다.

---

### 📊 분석 개요
- 종목: {decision['ticker']}
- 분석일: {decision['date']}
- 현재가: ${decision['price']}
- AI 신호: **{decision['signal']}**

---

### 🔍 주요 지표 요약 (최근 60일, {kdate(start_d)}~{kdate(end_d)})
{trend_summary}

---

### 🧭 작성 지침
**1. 요약 (2~3문장)**  
- 첫 문장은 "{decision['signal']}" 신호가 발생한 핵심 이유를 명시.  
- 상승·하락·전환 등 핵심 방향성 표현 포함.

**2. 지표별 분석 (상위 3개)**  
각 지표마다 아래 요소를 **순서대로** 포함:
- 초/중/후반 3구간의 수치 흐름과 추세 변화 (구체적 날짜/값)
- 급변 시점(최고·최저 날짜와 값)
- 시장적 의미 해석(모멘텀, 과매수/과매도, 전환 등)
- 신호({decision['signal']})를 지지하거나 경고하는 근거

**3. 종합 판단 (2~3문장)**  
- 3개 지표의 상관관계와 공통 흐름을 묘사  
- {decision['signal']} 신호의 신뢰도와 투자적 시사점 제시  

---

### ⚙️ 표현 원칙
- 문장 수: 10문장 이내  
- 수치: 소수점 2자리, 날짜: ‘X월 X일’ 형식  
- 모호 표현 금지 (‘전반적으로’, ‘다소’, ‘대체로’ 등)  
- 비유·은유 금지, 데이터 기반 논리만 사용  
- 문체: 간결하고 분석적, 감정 없는 객관 톤
""".strip()

    return prompt