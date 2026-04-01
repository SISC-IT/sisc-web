import json
import os
import re
import sys
from typing import Any, Dict, Iterable, List, Optional, Tuple

import requests

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn


class CompanyNameKoreanUpdater:
    """
    [회사명 한글 업데이트 모듈]
    company_names 테이블을 티커별로 갱신합니다.

    이름 결정 우선순위:
    1) 입력 이름이 이미 한글이면 그대로 사용
    2) KNOWN_KOREAN_NAMES 사전 매핑 사용
    3) 위키피디아 langlinks(ko) 조회
    4) Wikidata 라벨(ko) 조회
    5) LLM 번역 fallback
    6) 끝까지 실패하면 영문명 유지
    """

    WIKIPEDIA_API_URL = "https://en.wikipedia.org/w/api.php"
    WIKIDATA_API_URL = "https://www.wikidata.org/w/api.php"
    WIKIDATA_ENTITY_DATA_BASE_URL = "https://www.wikidata.org/wiki/Special:EntityData"
    USER_AGENT = (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    )

    # 자주 쓰이는 티커는 우선 하드코딩 매핑으로 처리하여 외부 API 의존도를 낮춥니다.
    # 참고: 위키 조회 실패/레이트리밋 상황에서도 즉시 한글명을 채우기 위한 사전입니다.
    KNOWN_KOREAN_NAMES = {
        # Mega-cap / Big Tech
        "AAPL": "애플",
        "MSFT": "마이크로소프트",
        "NVDA": "엔비디아",
        "GOOGL": "알파벳 클래스A",
        "GOOG": "알파벳",
        "AMZN": "아마존",
        "META": "메타",
        "TSLA": "테슬라",
        "NFLX": "넷플릭스",
        "ADBE": "어도비",
        "ORCL": "오라클",
        "CRM": "세일즈포스",
        "INTU": "인튜이트",
        "NOW": "서비스나우",
        "IBM": "아이비엠",
        "CSCO": "시스코",
        "PANW": "팔로알토네트웍스",
        "CRWD": "크라우드스트라이크",
        "DDOG": "데이터독",
        "ZS": "지스케일러",
        "OKTA": "옥타",
        "SNOW": "스노우플레이크",
        "PLTR": "팔란티어",
        "MDB": "몽고DB",
        "NET": "클라우드플레어",
        "DOCU": "도큐사인",
        "TWLO": "트윌리오",
        "TEAM": "아틀라시안",
        "UBER": "우버",
        "ABNB": "에어비앤비",
        "SHOP": "쇼피파이",
        "SQ": "블록",
        "PYPL": "페이팔",
        "EA": "일렉트로닉아츠",
        "TTD": "트레이드데스크",
        "ROKU": "로쿠",
        "SPOT": "스포티파이",
        "RBLX": "로블록스",
        "DELL": "델테크놀로지스",
        "HPQ": "에이치피",
        "SAP": "SAP",

        # Semiconductor
        "INTC": "인텔",
        "AMD": "에이엠디",
        "AVGO": "브로드컴",
        "QCOM": "퀄컴",
        "TXN": "텍사스인스트루먼트",
        "MU": "마이크론테크놀로지",
        "AMAT": "어플라이드머티어리얼즈",
        "LRCX": "램리서치",
        "KLAC": "케이엘에이",
        "ADI": "아날로그디바이시스",
        "MCHP": "마이크로칩테크놀로지",
        "MRVL": "마벨테크놀로지",
        "NXPI": "NXP반도체",
        "ON": "온세미컨덕터",
        "ASML": "ASML",
        "TSM": "TSMC",
        "SMCI": "슈퍼마이크로컴퓨터",
        "ARM": "암홀딩스",

        # Communication / Media / Internet
        "DIS": "월트디즈니",
        "CMCSA": "컴캐스트",
        "CHTR": "차터커뮤니케이션즈",
        "PARA": "파라마운트글로벌",
        "WBD": "워너브라더스디스커버리",
        "T": "에이티앤티",
        "VZ": "버라이즌",
        "TMUS": "T모바일US",
        "FOXA": "폭스코퍼레이션A",
        "FOX": "폭스코퍼레이션B",
        "BIDU": "바이두",
        "BABA": "알리바바",
        "JD": "징둥닷컴",
        "PDD": "핀둬둬",
        "TCEHY": "텐센트홀딩스",
        "NIO": "니오",
        "LI": "리오토",
        "XPEV": "샤오펑",

        # Financials
        "JPM": "JP모건체이스",
        "BAC": "뱅크오브아메리카",
        "WFC": "웰스파고",
        "C": "씨티그룹",
        "GS": "골드만삭스",
        "MS": "모건스탠리",
        "BLK": "블랙록",
        "SCHW": "찰스슈왑",
        "AXP": "아메리칸익스프레스",
        "V": "비자",
        "MA": "마스터카드",
        "COF": "캐피털원",
        "USB": "US뱅코프",
        "PNC": "PNC파이낸셜",
        "TFC": "트루이스트파이낸셜",
        "BK": "뱅크오브뉴욕멜론",
        "AIG": "에이아이지",
        "MET": "메트라이프",
        "PRU": "프루덴셜파이낸셜",
        "CB": "처브",
        "ALL": "올스테이트",
        "PGR": "프로그레시브",
        "TRV": "트래블러스",
        "MMC": "마시앤드맥레넌",
        "SPGI": "S&P글로벌",
        "MCO": "무디스",
        "ICE": "인터콘티넨털익스체인지",
        "CME": "CME그룹",
        "KKR": "케이케이알",
        "BX": "블랙스톤",
        "APO": "아폴로글로벌매니지먼트",

        # Healthcare / Pharma
        "UNH": "유나이티드헬스그룹",
        "JNJ": "존슨앤드존슨",
        "LLY": "일라이릴리",
        "MRK": "머크",
        "PFE": "화이자",
        "ABBV": "애브비",
        "ABT": "애보트래버러토리스",
        "BMY": "브리스톨마이어스스큅",
        "AMGN": "암젠",
        "GILD": "길리어드사이언스",
        "TMO": "써모피셔사이언티픽",
        "DHR": "다나허",
        "ISRG": "인튜이티브서지컬",
        "SYK": "스트라이커",
        "MDT": "메드트로닉",
        "BSX": "보스턴사이언티픽",
        "ZTS": "조에티스",
        "REGN": "리제네론",
        "VRTX": "버텍스파마슈티컬스",
        "CVS": "CVS헬스",
        "CI": "시그나",
        "HUM": "휴매나",
        "MRNA": "모더나",
        "BIIB": "바이오젠",
        "AZN": "아스트라제네카",
        "NVS": "노바티스",
        "SNY": "사노피",
        "GSK": "GSK",
        "RHHBY": "로슈홀딩",

        # Consumer
        "WMT": "월마트",
        "COST": "코스트코",
        "TGT": "타깃",
        "HD": "홈디포",
        "LOW": "로우스",
        "MCD": "맥도날드",
        "SBUX": "스타벅스",
        "NKE": "나이키",
        "PEP": "펩시코",
        "KO": "코카콜라",
        "PG": "프록터앤드갬블",
        "CL": "콜게이트팜올리브",
        "KMB": "킴벌리클라크",
        "EL": "에스티로더",
        "MDLZ": "몬델리즈",
        "KHC": "크래프트하인즈",
        "GIS": "제너럴밀스",
        "K": "켈로그",
        "HSY": "허쉬",
        "ADM": "아처대니얼스미들랜드",
        "MO": "알트리아",
        "PM": "필립모리스",
        "BTI": "브리티시아메리칸토바코",
        "UL": "유니레버",
        "DEO": "디아지오",

        # Industrials / Aerospace / Transport
        "GE": "GE에어로스페이스",
        "HON": "허니웰",
        "RTX": "알티엑스",
        "LMT": "록히드마틴",
        "NOC": "노스럽그러먼",
        "GD": "제너럴다이내믹스",
        "BA": "보잉",
        "CAT": "캐터필러",
        "DE": "디어앤컴퍼니",
        "UPS": "유피에스",
        "FDX": "페덱스",
        "UNP": "유니언퍼시픽",
        "NSC": "노퍽서던",
        "CSX": "씨에스엑스",
        "WM": "웨이스트매니지먼트",
        "RSG": "리퍼블릭서비스",
        "EMR": "에머슨일렉트릭",
        "ETN": "이튼",
        "ROK": "록웰오토메이션",
        "PH": "파커하니핀",
        "ITW": "일리노이툴웍스",
        "MMM": "쓰리엠",
        "AAL": "아메리칸에어라인스",
        "DAL": "델타항공",
        "UAL": "유나이티드항공홀딩스",
        "LUV": "사우스웨스트항공",

        # Energy / Utilities
        "XOM": "엑슨모빌",
        "CVX": "셰브론",
        "COP": "코노코필립스",
        "SLB": "슐럼버거",
        "EOG": "EOG리소시스",
        "OXY": "옥시덴털페트롤리엄",
        "MPC": "마라톤페트롤리엄",
        "PSX": "필립스66",
        "VLO": "발레로에너지",
        "KMI": "킨더모건",
        "WMB": "윌리엄스컴퍼니즈",
        "NEE": "넥스트에라에너지",
        "DUK": "듀크에너지",
        "SO": "서던컴퍼니",
        "D": "도미니언에너지",
        "AEP": "아메리칸일렉트릭파워",
        "EXC": "엑셀론",
        "SRE": "셈프라",
        "PCG": "피지앤이",
        "PEG": "퍼블릭서비스엔터프라이즈그룹",

        # REIT
        "AMT": "아메리칸타워",
        "PLD": "프로로지스",
        "EQIX": "에퀴닉스",
        "O": "리얼티인컴",
        "SPG": "사이먼프로퍼티그룹",
        "PSA": "퍼블릭스토리지",
        "WELL": "웰타워",
        "DLR": "디지털리얼티",
        "CCI": "크라운캐슬",

        # Auto / EV
        "GM": "제너럴모터스",
        "F": "포드",
        "RIVN": "리비안",
        "LCID": "루시드그룹",

        # Major ETFs
        "SPY": "SPDR S&P500 ETF",
        "QQQ": "인베스코 QQQ ETF",
        "DIA": "SPDR 다우존스 ETF",
        "IWM": "아이셰어스 러셀2000 ETF",
        "VTI": "뱅가드 토탈스톡마켓 ETF",
        "VOO": "뱅가드 S&P500 ETF",
        "IVV": "아이셰어스 코어 S&P500 ETF",
        "VEA": "뱅가드 선진국 ETF",
        "VWO": "뱅가드 신흥국 ETF",
        "EFA": "아이셰어스 MSCI EAFE ETF",
        "EEM": "아이셰어스 MSCI 신흥국 ETF",
        "TLT": "아이셰어스 20년국채 ETF",
        "IEF": "아이셰어스 7-10년국채 ETF",
        "SHY": "아이셰어스 1-3년국채 ETF",
        "LQD": "아이셰어스 투자등급회사채 ETF",
        "HYG": "아이셰어스 하이일드회사채 ETF",
        "GLD": "SPDR 골드 ETF",
        "SLV": "아이셰어스 실버 ETF",
        "USO": "유나이티드스테이츠오일 ETF",
        "UNG": "유나이티드스테이츠천연가스 ETF",
        "XLF": "파이낸셜셀렉트섹터 ETF",
        "XLK": "테크놀로지셀렉트섹터 ETF",
        "XLE": "에너지셀렉트섹터 ETF",
        "XLV": "헬스케어셀렉트섹터 ETF",
        "XLI": "산업재셀렉트섹터 ETF",
        "XLP": "필수소비재셀렉트섹터 ETF",
        "XLY": "임의소비재셀렉트섹터 ETF",
        "XLU": "유틸리티셀렉트섹터 ETF",
        "XLB": "소재셀렉트섹터 ETF",
        "XLRE": "부동산셀렉트섹터 ETF",
        "XLC": "커뮤니케이션셀렉트섹터 ETF",
        "SOXX": "아이셰어스 반도체 ETF",
        "SMH": "반에크 반도체 ETF",
        "ARKK": "아크이노베이션ETF",
        "VNQ": "뱅가드 리츠 ETF",
        "SCHD": "슈왑 미국배당 ETF",
        "JEPI": "JP모건 에쿼티프리미엄인컴 ETF",
        "NOBL": "프로셰어즈 배당귀족 ETF",
        "BND": "뱅가드 토탈채권 ETF",
        "AGG": "아이셰어스 코어 미국채권 ETF",
        "DGRO": "아이셰어스 코어 배당성장 ETF",
        "VYM": "뱅가드 고배당 ETF",
        "IGV": "아이셰어스 확장기술소프트웨어 ETF",

        # Additional manual mapping (unresolved universe batch)
        "ACGL": "아치 캐피털 그룹",
        "AEE": "아메런",
        "AES": "AES 코퍼레이션",
        "AFL": "애플랙",
        "AJG": "아서 J. 갤러거",
        "ALB": "알버말",
        "ALGN": "얼라인 테크놀로지",
        "ALLE": "앨리전",
        "AMCR": "앰코어",
        "AME": "아메텍",
        "APA": "APA 코퍼레이션",
        "APH": "암페놀",
        "ARE": "알렉산드리아 리얼에스테이트 에쿼티스",
        "ARES": "아레스 매니지먼트",
        "ATO": "애트모스 에너지",
        "AVB": "아발론베이 커뮤니티스",
        "AVY": "에이버리 데니슨",
        "AWK": "아메리칸 워터 웍스",
        "AXON": "액손 엔터프라이즈",
        "BDX": "벡턴 디킨슨",
        "BF-B": "브라운 포맨",
        "BG": "번지 글로벌",
        "BLDR": "빌더스 퍼스트소스",
        "BR": "브로드리지 파이낸셜 솔루션즈",
        "BRO": "브라운 앤 브라운",
        "BXP": "비엑스피",
        "CAH": "카디널 헬스",
        "CBOE": "시보 글로벌 마켓",
        "CBRE": "CBRE 그룹",
        "CDW": "CDW 코퍼레이션",
        "CEG": "컨스텔레이션 에너지",
        "CF": "CF 인더스트리즈",
        "CFG": "시티즌스 파이낸셜 그룹",
        "CHD": "처치 앤 드와이트",
        "CHRW": "C.H. 로빈슨",
        "CIEN": "시에나",
        "CINF": "신시내티 파이낸셜",
        "CMS": "CMS 에너지",
        "CNC": "센틴",
        "CNP": "센터포인트 에너지",
        "COO": "쿠퍼 컴퍼니즈",
        "CPB": "캠벨스 컴퍼니",
        "CPRT": "코파트",
        "CPT": "캠던 프로퍼티 트러스트",
        "CRL": "찰스리버 래버러토리스",
        "CSGP": "코스타 그룹",
        "CTAS": "신타스",
        "CTRA": "코테라 에너지",
        "DECK": "데커스 브랜즈",
        "DGX": "퀘스트 다이아그노스틱스",
        "DHI": "D.R. 호튼",
        "DLTR": "달러 트리",
        "DOC": "헬스피크 프로퍼티스",
        "DRI": "다든 레스토랑",
        "DTE": "DTE 에너지",
        "ED": "콘솔리데이티드 에디슨",
        "EG": "에버레스트 그룹",
        "ELV": "엘리번스 헬스",
        "EPAM": "EPAM 시스템즈",
        "EQR": "에쿼티 레지덴셜",
        "EQT": "EQT 코퍼레이션",
        "ERIE": "이리 인뎀니티",
        "ES": "에버소스 에너지",
        "ESS": "에식스 프로퍼티 트러스트",
        "ETR": "엔터지",
        "EVRG": "에버지",
        "EW": "에드워즈 라이프사이언시스",
        "EXE": "익스팬드 에너지",
        "EXPD": "익스피다이터스 인터내셔널",
        "EXR": "엑스트라 스페이스 스토리지",
        "FANG": "다이아몬드백 에너지",
        "FAST": "패스널",
        "FICO": "페어 아이작",
        "FIS": "피델리티 내셔널 인포메이션 서비스",
        "FITB": "피프스 서드 뱅코프",
        "FIX": "컴포트 시스템즈 USA",
        "FRT": "페더럴 리얼티 인베스트먼트 트러스트",
        "FSLR": "퍼스트 솔라",
        "FTV": "포티브",
        "GNRC": "제네락",
        "GPC": "제뉴인 파츠 컴퍼니",
        "GPN": "글로벌 페이먼츠",
        "GWW": "W.W. 그레인저",
        "HBAN": "헌팅턴 뱅크셰어스",
        "HCA": "HCA 헬스케어",
        "HIG": "하트퍼드",
        "HOLX": "홀로직",
        "HSIC": "헨리 샤인",
        "HST": "호스트 호텔스 앤 리조트",
        "HUBB": "허벨",
        "HWM": "하우멧 에어로스페이스",
        "IDXX": "아이덱스 래버러토리스",
        "IEX": "IDEX 코퍼레이션",
        "IFF": "인터내셔널 플레이버스 앤 프래그런스",
        "INCY": "인사이트",
        "INVH": "인비테이션 홈즈",
        "IR": "잉거솔 랜드",
        "IRM": "아이언 마운틴",
        "J": "제이콥스 솔루션스",
        "JBHT": "J.B. 헌트",
        "JKHY": "잭 헨리 앤 어소시에이츠",
        "KDP": "큐리그 닥터페퍼",
        "KEY": "키코프",
        "KIM": "킴코 리얼티",
        "L": "로우스 코퍼레이션",
        "LDOS": "레이도스",
        "LEN": "레나",
        "LH": "랩코프",
        "LHX": "L3해리스",
        "LII": "레녹스 인터내셔널",
        "LYB": "라이온델바젤",
        "MAA": "미드아메리카 아파트먼트 커뮤니티스",
        "MKC": "맥코믹 앤 컴퍼니",
        "MLM": "마틴 마리에타 머티리얼즈",
        "MOH": "몰리나 헬스케어",
        "MOS": "모자이크",
        "MPWR": "모놀리식 파워 시스템즈",
        "MSCI": "엠에스씨아이",
        "MTCH": "매치 그룹",
        "NCLH": "노르웨이지안 크루즈 라인 홀딩스",
        "NDSN": "노드슨",
        "NI": "나이소스",
        "NRG": "NRG 에너지",
        "NVR": "엔브이알",
        "NWS": "뉴스 코프 B",
        "NWSA": "뉴스 코프 A",
        "ORLY": "오라일리 오토모티브",
        "PAYX": "페이첵스",
        "PFG": "프린시펄 파이낸셜 그룹",
        "PHM": "풀트그룹",
        "PKG": "패키징 코퍼레이션 오브 아메리카",
        "PNR": "펜테어",
        "PNW": "피너클 웨스트 캐피털",
        "PODD": "인슐렛",
        "POOL": "풀 코퍼레이션",
        "PPL": "PPL 코퍼레이션",
        "PWR": "퀀타 서비스",
        "Q": "큐니티 일렉트로닉스",
        "RCL": "로열 캐리비안 그룹",
        "REG": "리젠시 센터스",
        "RF": "리전스 파이낸셜",
        "RMD": "레즈메드",
        "ROL": "롤린스",
        "ROP": "로퍼 테크놀로지스",
        "RVTY": "레비티",
        "SBAC": "SBA 커뮤니케이션스",
        "SJM": "J.M. 스머커",
        "SOLV": "솔벤텀",
        "STE": "스테리스",
        "STLD": "스틸 다이내믹스",
        "STZ": "컨스텔레이션 브랜즈",
        "SW": "스머핏 웨스트록",
        "SWKS": "스카이웍스 솔루션즈",
        "SYF": "싱크로니 파이낸셜",
        "TDG": "트랜스다임 그룹",
        "TDY": "텔레다인 테크놀로지스",
        "TECH": "바이오테크네",
        "TJX": "TJX 컴퍼니스",
        "TKO": "TKO 그룹 홀딩스",
        "TPL": "텍사스 퍼시픽 랜드",
        "TPR": "태피스트리",
        "TRGP": "타르가 리소시스",
        "TRMB": "트림블",
        "TSCO": "트랙터 서플라이",
        "TT": "트레인 테크놀로지스",
        "TYL": "타일러 테크놀로지스",
        "UDR": "유디알",
        "UHS": "유니버설 헬스 서비스",
        "URI": "유나이티드 렌털스",
        "VICI": "비치 프로퍼티스",
        "VLTO": "베랄토",
        "VMC": "벌컨 머티리얼즈",
        "VRSK": "베리스크 애널리틱스",
        "VST": "비스트라",
        "WAT": "워터스 코퍼레이션",
        "WEC": "WEC 에너지 그룹",
        "WRB": "W.R. 버클리",
        "WSM": "윌리엄스 소노마",
        "WST": "웨스트 파마슈티컬 서비스",
        "XYL": "자일럼",
        "ZBH": "짐머 바이오메트",

        # Crypto ticker aliases
        "BTC-USD": "비트코인",
        "ETH-USD": "이더리움",

        # Korea major
        "005930.KS": "삼성전자",
        "000660.KS": "SK하이닉스",
        "035420.KS": "NAVER",
        "051910.KS": "LG화학",
        "035720.KS": "카카오",
        "207940.KS": "삼성바이오로직스",
        "005380.KS": "현대자동차",
        "000270.KS": "기아",
        "012330.KS": "현대모비스",
        "068270.KS": "셀트리온",
        "066570.KS": "LG전자",
        "003550.KS": "LG",
        "096770.KS": "SK이노베이션",
        "017670.KS": "SK텔레콤",
        "034730.KS": "SK",
        "015760.KS": "한국전력",
        "105560.KS": "KB금융",
        "055550.KS": "신한지주",
        "086790.KS": "하나금융지주",
        "024110.KS": "기업은행",
    }

    def __init__(
        self,
        db_name: str = "db",
        timeout_sec: int = 8,
        enable_llm_fallback: bool = True,
        llm_provider: str = "gemini",
        llm_model: Optional[str] = None,
    ):
        self.db_name = db_name
        self.timeout_sec = timeout_sec
        self.enable_llm_fallback = enable_llm_fallback
        self.llm_provider = llm_provider
        self.llm_model = llm_model
        # 같은 영문명으로 위키 API를 반복 호출하지 않기 위한 캐시
        self._ko_name_cache: Dict[str, Optional[str]] = {}
        self._wikidata_ko_cache: Dict[str, Optional[str]] = {}
        self._llm_ko_cache: Dict[str, Optional[str]] = {}
        self._llm_client = None
        self._llm_unavailable_reason: Optional[str] = None

    @staticmethod
    def _normalize_ticker(ticker: str) -> str:
        # DB 저장/조회 일관성을 위해 티커를 대문자 표준형으로 정규화
        return str(ticker or "").strip().upper()

    @staticmethod
    def _contains_hangul(text: str) -> bool:
        # 문자열 안에 한글 음절(가-힣)이 하나라도 있으면 True
        if not text:
            return False
        return any("가" <= ch <= "힣" for ch in text)

    @staticmethod
    def _sanitize_external_name(raw_name: str) -> str:
        """
        위키/위키데이터에서 가져온 표시명을 저장하기 전에 정리합니다.
        - '(기업)' 문구 제거
        - 말미 괄호 분류(기업/회사/브랜드/법인 등) 제거
        - 특수문자 제거(한글/영문/숫자/공백만 유지)
        """
        cleaned = str(raw_name or "").strip()
        if not cleaned:
            return ""

        # 요청사항: '(기업)' 하드코딩 제거
        cleaned = cleaned.replace("(기업)", " ")
        cleaned = cleaned.replace("（기업）", " ")

        # 위키 분류성 꼬리표 제거
        cleaned = re.sub(
            r"\s*\((?:[^)]*(?:기업|회사|브랜드|법인|지주|주식회사)[^)]*)\)\s*$",
            "",
            cleaned,
        )

        # 특수문자 필터링: 한글/영문/숫자/공백만 유지
        cleaned = re.sub(r"[^가-힣A-Za-z0-9\s]", "", cleaned)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()
        return cleaned

    def _lookup_ko_name_in_wikipedia(self, english_name: str) -> Optional[str]:
        """
        영문 회사명 기준으로 위키피디아 한국어 문서 제목을 조회합니다.
        성공하면 한글 제목(회사명)을 반환하고, 실패하면 None을 반환합니다.
        """
        normalized_name = str(english_name or "").strip()
        if not normalized_name:
            return None

        if normalized_name in self._ko_name_cache:
            return self._ko_name_cache[normalized_name]

        params = {
            "action": "query",
            "format": "json",
            "titles": normalized_name,
            "prop": "langlinks",
            "lllang": "ko",
            "redirects": "1",
        }
        headers = {"User-Agent": self.USER_AGENT}

        ko_name: Optional[str] = None
        try:
            response = requests.get(
                self.WIKIPEDIA_API_URL,
                params=params,
                headers=headers,
                timeout=self.timeout_sec,
            )
            response.raise_for_status()
            payload = response.json()

            pages = ((payload or {}).get("query") or {}).get("pages") or {}
            for page in pages.values():
                langlinks = page.get("langlinks") or []
                if not langlinks:
                    continue
                candidate = langlinks[0].get("*") or langlinks[0].get("title")
                if candidate:
                    normalized_candidate = self._sanitize_external_name(str(candidate))
                    if normalized_candidate:
                        ko_name = normalized_candidate
                        break
        except Exception:
            ko_name = None

        self._ko_name_cache[normalized_name] = ko_name
        return ko_name

    def _fetch_wikidata_ko_label(self, qid: str) -> Optional[str]:
        if not qid:
            return None

        headers = {"User-Agent": self.USER_AGENT}
        try:
            response = requests.get(
                f"{self.WIKIDATA_ENTITY_DATA_BASE_URL}/{qid}.json",
                headers=headers,
                timeout=self.timeout_sec,
            )
            response.raise_for_status()
            payload = response.json()
            entity = ((payload or {}).get("entities") or {}).get(qid) or {}
            labels = entity.get("labels") or {}
            ko_label = (labels.get("ko") or {}).get("value")
            if ko_label:
                normalized_label = self._sanitize_external_name(str(ko_label))
                if normalized_label:
                    return normalized_label
        except Exception:
            return None
        return None

    def _lookup_ko_name_in_wikidata(self, english_name: str) -> Optional[str]:
        """
        Wikidata에서 영문 회사명을 검색해 한국어 라벨(ko)을 찾습니다.
        """
        normalized_name = str(english_name or "").strip()
        if not normalized_name:
            return None

        if normalized_name in self._wikidata_ko_cache:
            return self._wikidata_ko_cache[normalized_name]

        params = {
            "action": "wbsearchentities",
            "search": normalized_name,
            "language": "en",
            "type": "item",
            "limit": "5",
            "format": "json",
        }
        headers = {"User-Agent": self.USER_AGENT}

        ko_name: Optional[str] = None
        try:
            response = requests.get(
                self.WIKIDATA_API_URL,
                params=params,
                headers=headers,
                timeout=self.timeout_sec,
            )
            response.raise_for_status()
            payload = response.json()
            items = (payload or {}).get("search") or []
            for item in items:
                qid = str(item.get("id") or "").strip()
                if not qid:
                    continue

                ko_label = self._fetch_wikidata_ko_label(qid)
                if ko_label:
                    ko_name = ko_label
                    break
        except Exception:
            ko_name = None

        self._wikidata_ko_cache[normalized_name] = ko_name
        return ko_name

    def _resolve_korean_name(self, ticker: str, english_name: str) -> str:
        """
        최종 저장할 회사명을 결정합니다.
        - 최대한 한글명을 확보하되, 불가능하면 영문명을 보존합니다.
        """
        normalized_ticker = self._normalize_ticker(ticker)
        normalized_name = str(english_name or "").strip()

        if not normalized_name:
            # 이름이 전혀 없으면 최소한 티커를 저장
            return normalized_ticker

        if self._contains_hangul(normalized_name):
            # 이미 한글이면 추가 변환 없이 그대로 사용
            return normalized_name

        if normalized_ticker in self.KNOWN_KOREAN_NAMES:
            return self.KNOWN_KOREAN_NAMES[normalized_ticker]

        wiki_name = self._lookup_ko_name_in_wikipedia(normalized_name)
        if wiki_name:
            return wiki_name

        wikidata_name = self._lookup_ko_name_in_wikidata(normalized_name)
        if wikidata_name:
            return wikidata_name

        llm_name = self._lookup_ko_name_with_llm(normalized_ticker, normalized_name)
        if llm_name:
            return llm_name

        return normalized_name

    def fetch_untranslated_company_names(self, limit: Optional[int] = None) -> List[Tuple[str, str]]:
        """
        company_names에서 한글이 포함되지 않은 행을 조회합니다.
        반환 형식: [(ticker, company_name), ...]
        """
        conn = None
        cursor = None
        try:
            conn = get_db_conn(self.db_name)
            cursor = conn.cursor()

            query = """
                SELECT ticker, company_name
                FROM public.company_names
                WHERE company_name IS NULL
                   OR btrim(company_name) = ''
                   OR company_name !~ '[가-힣]'
                ORDER BY ticker
            """
            if limit is not None and int(limit) > 0:
                query += " LIMIT %s"
                cursor.execute(query, (int(limit),))
            else:
                cursor.execute(query)

            rows = cursor.fetchall() or []
            result: List[Tuple[str, str]] = []
            for row in rows:
                if not row or not row[0]:
                    continue
                ticker = self._normalize_ticker(str(row[0]))
                english_name = str(row[1] or "").strip()
                result.append((ticker, english_name))
            return result
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()

    @staticmethod
    def _extract_first_json_object(raw_text: str) -> Optional[Dict[str, Any]]:
        if not raw_text:
            return None

        stripped = raw_text.strip()
        if stripped.startswith("```"):
            stripped = stripped.replace("```json", "").replace("```", "").strip()

        try:
            payload = json.loads(stripped)
            if isinstance(payload, dict):
                return payload
        except Exception:
            pass

        start = stripped.find("{")
        end = stripped.rfind("}")
        if start == -1 or end == -1 or end <= start:
            return None

        try:
            payload = json.loads(stripped[start : end + 1])
            if isinstance(payload, dict):
                return payload
        except Exception:
            return None
        return None

    def _sanitize_korean_name(self, name: str) -> Optional[str]:
        if not name:
            return None
        cleaned = " ".join(str(name).strip().split())
        cleaned = cleaned.strip("`'\"")
        if not cleaned:
            return None
        if len(cleaned) > 100:
            return None
        if not self._contains_hangul(cleaned):
            return None
        return cleaned

    def _parse_korean_name_from_llm_output(self, raw_text: str) -> Optional[str]:
        payload = self._extract_first_json_object(raw_text)
        if payload:
            for key in ("korean_name", "company_name_ko", "company_name", "translation", "name"):
                value = payload.get(key)
                if isinstance(value, str):
                    parsed = self._sanitize_korean_name(value)
                    if parsed:
                        return parsed

        lines = (raw_text or "").strip().splitlines()
        if lines:
            parsed = self._sanitize_korean_name(lines[0])
            if parsed:
                return parsed
        return None

    def _build_llm_client(self, provider: str = "gemini", model_name: Optional[str] = None):
        provider_name = str(provider or "gemini").strip().lower()
        if provider_name == "gemini":
            from AI.libs.llm import GeminiClient

            resolved_model = model_name or os.environ.get("GEMINI_MODEL", "gemini-2.0-flash")
            return GeminiClient(model_name=resolved_model)

        if provider_name == "groq":
            from AI.libs.llm import GroqClient

            resolved_model = model_name or os.environ.get("GROQ_MODEL", "llama-3.3-70b-versatile")
            return GroqClient(model_name=resolved_model)

        if provider_name == "ollama":
            from AI.libs.llm import OllamaClient

            resolved_model = model_name or os.environ.get("OLLAMA_MODEL", "llama3:latest")
            return OllamaClient(model_name=resolved_model)

        raise ValueError(f"Unsupported provider: {provider_name}")

    def _get_llm_client_or_none(self):
        if not self.enable_llm_fallback:
            return None

        if self._llm_unavailable_reason:
            return None

        if self._llm_client is not None:
            return self._llm_client

        try:
            self._llm_client = self._build_llm_client(
                provider=self.llm_provider,
                model_name=self.llm_model,
            )
            return self._llm_client
        except Exception as e:
            self._llm_unavailable_reason = str(e)
            print(f"[CompanyName][LLM][Warn] LLM fallback 비활성화: {self._llm_unavailable_reason}")
            return None

    @staticmethod
    def _is_llm_quota_or_rate_error(error_text: str) -> bool:
        text = str(error_text or "").lower()
        if not text:
            return False
        signals = (
            "resource_exhausted",
            "quota exceeded",
            "rate limit",
            "too many requests",
            "free_tier_requests",
            "free_tier_input_token_count",
            "429",
        )
        return any(sig in text for sig in signals)

    def _lookup_ko_name_with_llm(self, ticker: str, english_name: str) -> Optional[str]:
        normalized_ticker = self._normalize_ticker(ticker)
        normalized_name = str(english_name or "").strip()
        cache_key = f"{normalized_ticker}|{normalized_name.lower()}"

        if cache_key in self._llm_ko_cache:
            return self._llm_ko_cache[cache_key]

        llm_client = self._get_llm_client_or_none()
        if not llm_client:
            self._llm_ko_cache[cache_key] = None
            return None

        ko_name = self._translate_name_with_llm(
            ticker=normalized_ticker,
            english_name=normalized_name,
            llm_client=llm_client,
        )
        if not ko_name:
            llm_error = str(getattr(llm_client, "last_error", "") or "")
            if self._is_llm_quota_or_rate_error(llm_error):
                self._llm_unavailable_reason = "LLM quota/rate exceeded"
                print(
                    "[CompanyName][LLM][Warn] 쿼터/레이트 제한 감지로 현재 실행에서 LLM fallback을 중단합니다."
                )

        if ko_name:
            print(f"[CompanyName][LLM][Fallback] {normalized_ticker} => {ko_name}")

        self._llm_ko_cache[cache_key] = ko_name
        return ko_name

    def _translate_name_with_llm(self, ticker: str, english_name: str, llm_client) -> Optional[str]:
        source_name = str(english_name or "").strip() or ticker
        if self._contains_hangul(source_name):
            return source_name

        system_prompt = (
            "너는 미국 주식/ETF 종목명을 한국어 표시명으로 번역하는 도우미다. "
            "응답은 반드시 JSON 한 줄만 출력한다."
        )
        prompt = (
            "아래 종목명을 한국 투자자에게 익숙한 한글 회사명으로 번역해줘.\n"
            f"- ticker: {ticker}\n"
            f"- english_name: {source_name}\n\n"
            "출력 형식(반드시 지킬 것):\n"
            '{"korean_name":"한글명"}\n\n'
            "규칙:\n"
            "1) 불필요한 설명/주석/마크다운 금지\n"
            "2) 회사명만 출력\n"
            "3) 한글이 불가능하면 빈 문자열 출력"
        )
        raw = llm_client.generate_text(
            prompt=prompt,
            system_prompt=system_prompt,
            temperature=0.0,
            max_tokens=120,
            top_p=0.1,
        )
        return self._parse_korean_name_from_llm_output(raw)

    def translate_unresolved_with_llm(
        self,
        limit: Optional[int] = None,
        provider: str = "gemini",
        model_name: Optional[str] = None,
        dry_run: bool = False,
        commit_every: int = 20,
    ) -> Dict[str, int]:
        """
        company_names에서 한글 미포함 행만 대상으로 LLM 번역을 수행합니다.
        """
        targets = self.fetch_untranslated_company_names(limit=limit)
        total = len(targets)
        if total == 0:
            print("[CompanyName][LLM] 번역 대상이 없습니다.")
            return {"total": 0, "translated": 0, "failed": 0}

        llm_client = self._build_llm_client(provider=provider, model_name=model_name)
        health_ok = llm_client.get_health()
        if not health_ok:
            error = getattr(llm_client, "last_error", None)
            print(f"[CompanyName][LLM][Warn] 헬스체크 실패: {error}")

        conn = None
        cursor = None
        translated = 0
        failed = 0
        try:
            if not dry_run:
                conn = get_db_conn(self.db_name)
                cursor = conn.cursor()

            for idx, (ticker, english_name) in enumerate(targets, start=1):
                source_name = english_name or ticker
                korean_name = self._translate_name_with_llm(
                    ticker=ticker,
                    english_name=source_name,
                    llm_client=llm_client,
                )
                if not korean_name:
                    failed += 1
                    print(f"[CompanyName][LLM][Fail] {idx}/{total} {ticker} | {source_name}")
                    llm_error = str(getattr(llm_client, "last_error", "") or "")
                    if self._is_llm_quota_or_rate_error(llm_error):
                        print("[CompanyName][LLM][Warn] 쿼터/레이트 제한으로 배치를 조기 종료합니다.")
                        break
                    continue

                if dry_run:
                    print(f"[CompanyName][LLM][DryRun] {ticker}\t{source_name}\t=>\t{korean_name}")
                    translated += 1
                    continue

                self._upsert_company_name(ticker=ticker, company_name=korean_name, cursor=cursor)
                translated += 1
                print(f"[CompanyName][LLM][OK] {idx}/{total} {ticker} => {korean_name}")

                if commit_every > 0 and translated % commit_every == 0:
                    conn.commit()

            if conn:
                conn.commit()
        except Exception as e:
            if conn:
                conn.rollback()
            print(f"[CompanyName][LLM][Error] 배치 실패: {e}")
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()

        print(
            f"[CompanyName][LLM] 완료: total={total}, translated={translated}, failed={failed}, dry_run={dry_run}"
        )
        return {"total": total, "translated": translated, "failed": failed}

    def _upsert_company_name(self, ticker: str, company_name: str, cursor) -> None:
        # UNIQUE 제약 충돌이 발생해도 트랜잭션이 죽지 않도록 우선 DO NOTHING
        insert_query = """
            INSERT INTO public.company_names (ticker, company_name)
            VALUES (%s, %s)
            ON CONFLICT DO NOTHING;
        """
        cursor.execute(insert_query, (ticker, company_name))

        # company_name 중복 충돌이 없는 경우에만 ticker 기준으로 안전 업데이트
        safe_update_query = """
            UPDATE public.company_names cn
            SET company_name = %s
            WHERE cn.ticker = %s
              AND NOT EXISTS (
                  SELECT 1
                  FROM public.company_names c2
                  WHERE c2.company_name = %s
                    AND c2.ticker <> %s
              );
        """
        cursor.execute(safe_update_query, (company_name, ticker, company_name, ticker))

        # company_name 중복으로 ticker row가 생성되지 못한 경우 fallback 이름으로 재시도
        cursor.execute(
            "SELECT 1 FROM public.company_names WHERE ticker = %s;",
            (ticker,),
        )
        exists = cursor.fetchone()
        if exists:
            return

        fallback_name = f"{company_name} ({ticker})"
        cursor.execute(insert_query, (ticker, fallback_name))

    def upsert_company_name(
        self,
        ticker: str,
        english_name: Optional[str] = None,
        cursor=None,
    ) -> bool:
        """
        단일 티커의 company_names를 갱신합니다.

        cursor를 외부에서 전달하면 해당 트랜잭션 컨텍스트를 재사용하고,
        전달하지 않으면 내부에서 연결을 열고 커밋/종료까지 수행합니다.
        """
        normalized_ticker = self._normalize_ticker(ticker)
        if not normalized_ticker:
            return False

        own_connection = cursor is None
        conn = None
        local_cursor = cursor

        try:
            if own_connection:
                conn = get_db_conn(self.db_name)
                local_cursor = conn.cursor()

            if not english_name:
                # 힌트 이름이 없으면 기존 company_names의 이름을 영문명 후보로 사용
                local_cursor.execute(
                    "SELECT company_name FROM public.company_names WHERE ticker = %s;",
                    (normalized_ticker,),
                )
                row = local_cursor.fetchone()
                if row and row[0]:
                    english_name = str(row[0]).strip()

            resolved_name = self._resolve_korean_name(normalized_ticker, english_name or normalized_ticker)
            self._upsert_company_name(normalized_ticker, resolved_name, local_cursor)

            if own_connection:
                conn.commit()
            return True
        except Exception as e:
            if own_connection and conn:
                conn.rollback()
            print(f"[CompanyName][Error] {normalized_ticker} 처리 실패: {e}")
            return False
        finally:
            if own_connection and local_cursor:
                local_cursor.close()
            if own_connection and conn:
                conn.close()

    def update_tickers(self, tickers: Iterable[str]) -> int:
        """
        여러 티커를 한 번에 갱신합니다.
        반환값은 성공적으로 처리한 티커 수입니다.
        """
        normalized_tickers = []
        seen = set()
        for ticker in tickers:
            normalized = self._normalize_ticker(ticker)
            if not normalized or normalized in seen:
                continue
            normalized_tickers.append(normalized)
            seen.add(normalized)

        if not normalized_tickers:
            return 0

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        success_count = 0
        try:
            for ticker in normalized_tickers:
                if self.upsert_company_name(ticker=ticker, cursor=cursor):
                    success_count += 1
            conn.commit()
            return success_count
        except Exception as e:
            conn.rollback()
            print(f"[CompanyName][Fatal] 배치 업데이트 실패: {e}")
            return success_count
        finally:
            cursor.close()
            conn.close()


if __name__ == "__main__":
    import argparse
    from AI.libs.database.ticker_loader import load_all_tickers_from_db

    parser = argparse.ArgumentParser(description="티커별 company_names 한글명 업데이트")
    parser.add_argument("tickers", nargs="*", help="업데이트할 티커 (예: AAPL TSLA)")
    parser.add_argument("--db", default="db", help="DB 이름")
    parser.add_argument("--name", default=None, help="단일 티커용 영문 회사명 힌트")
    parser.add_argument("--all", action="store_true", help="DB(stock_info) 전체 티커를 대상 처리")
    args = parser.parse_args()

    updater = CompanyNameKoreanUpdater(db_name=args.db)

    # 사용자가 티커를 안 주면 기본값으로 전체 티커를 처리합니다.
    use_all = args.all or not args.tickers

    if use_all:
        try:
            target_tickers = load_all_tickers_from_db(verbose=False)
        except Exception as e:
            print(f"[CompanyName][Error] DB 전체 티커 로드 실패: {e}")
            sys.exit(1)

        if not target_tickers:
            print("[CompanyName] 처리할 티커가 없습니다.")
            sys.exit(0)

        count = updater.update_tickers(target_tickers)
        print(f"[CompanyName] 완료(전체): {count}/{len(target_tickers)}개 처리")
        sys.exit(0)

    # 단일 티커 + --name 힌트가 있으면 단건 전용 로직 사용
    if args.name and len(args.tickers) == 1:
        updated = updater.upsert_company_name(args.tickers[0], english_name=args.name)
        print(f"[CompanyName] {'성공' if updated else '실패'}: {args.tickers[0]}")
        sys.exit(0)

    count = updater.update_tickers(args.tickers)
    print(f"[CompanyName] 완료: {count}/{len(args.tickers)}개 처리")
