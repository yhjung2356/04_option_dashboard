#!/usr/bin/env python3
"""
한투 API로 실제 202601 옵션 종목코드 확인
"""
import requests
import json
import os
from datetime import datetime
from dotenv import load_dotenv

# .env 파일에서 환경변수 로드
load_dotenv()

# 환경변수에서 API 키 읽기
APP_KEY = os.environ.get('KIS_APP_KEY', '')
APP_SECRET = os.environ.get('KIS_APP_SECRET', '')
BASE_URL = "https://openapi.koreainvestment.com:9443"

def get_access_token():
    """접근 토큰 발급"""
    url = f"{BASE_URL}/oauth2/tokenP"
    headers = {"content-type": "application/json"}
    body = {
        "grant_type": "client_credentials",
        "appkey": APP_KEY,
        "appsecret": APP_SECRET
    }
    
    response = requests.post(url, headers=headers, data=json.dumps(body))
    if response.status_code == 200:
        return response.json()['access_token']
    else:
        print(f"토큰 발급 실패: {response.text}")
        return None

def get_option_list(access_token):
    """202601 옵션 월물 리스트 조회"""
    url = f"{BASE_URL}/uapi/domestic-futureoption/v1/quotations/display-board-option-list"
    
    headers = {
        "content-type": "application/json",
        "authorization": f"Bearer {access_token}",
        "appkey": APP_KEY,
        "appsecret": APP_SECRET,
        "tr_id": "FHPIO056104C0"
    }
    
    params = {
        "FID_COND_SCR_DIV_CODE": "509",
        "FID_COND_MRKT_DIV_CODE": "",
        "FID_COND_MRKT_CLS_CODE": ""
    }
    
    response = requests.get(url, headers=headers, params=params)
    if response.status_code == 200:
        data = response.json()
        print("\n=== 옵션 월물 리스트 ===")
        print(json.dumps(data, indent=2, ensure_ascii=False))
        return data
    else:
        print(f"월물 리스트 조회 실패: {response.text}")
        return None

def get_callput_board(access_token, month_code="202601"):
    """202601 콜풋 전광판 조회"""
    url = f"{BASE_URL}/uapi/domestic-futureoption/v1/quotations/display-board-callput"
    
    headers = {
        "content-type": "application/json",
        "authorization": f"Bearer {access_token}",
        "appkey": APP_KEY,
        "appsecret": APP_SECRET,
        "tr_id": "FHPIF05030100"
    }
    
    params = {
        "FID_COND_MRKT_DIV_CODE": "O",
        "FID_COND_SCR_DIV_CODE": "20503",
        "FID_MRKT_CLS_CODE": "CO",
        "FID_MTRT_CNT": month_code,
        "FID_MRKT_CLS_CODE1": "PO",
        "FID_COND_MRKT_CLS_CODE": ""
    }
    
    print(f"\n=== {month_code} 콜풋 전광판 조회 중... ===")
    response = requests.get(url, headers=headers, params=params)
    
    if response.status_code == 200:
        data = response.json()
        
        # 콜옵션 종목코드 출력 (상위 5개만)
        if 'output1' in data and len(data['output1']) > 0:
            print(f"\n=== {month_code} 콜옵션 종목코드 (상위 5개) ===")
            for i, item in enumerate(data['output1'][:5]):
                code = item.get('optn_shrn_iscd', 'N/A')
                strike = item.get('acpr', 'N/A')
                price = item.get('optn_prpr', '0')
                print(f"{i+1}. {code} | 행사가: {strike} | 현재가: {price}")
        
        # 풋옵션 종목코드 출력 (상위 5개만)
        if 'output2' in data and len(data['output2']) > 0:
            print(f"\n=== {month_code} 풋옵션 종목코드 (상위 5개) ===")
            for i, item in enumerate(data['output2'][:5]):
                code = item.get('optn_shrn_iscd', 'N/A')
                strike = item.get('acpr', 'N/A')
                price = item.get('optn_prpr', '0')
                print(f"{i+1}. {code} | 행사가: {strike} | 현재가: {price}")
        
        return data
    else:
        print(f"전광판 조회 실패: {response.text}")
        return None

if __name__ == "__main__":
    print("=" * 60)
    print("한투 API 202601 옵션 종목코드 확인")
    print("=" * 60)
    
    # API 키 확인
    if not APP_KEY or not APP_SECRET:
        print("\n⚠️  환경변수 설정 필요:")
        print("   set KIS_APP_KEY=your_app_key")
        print("   set KIS_APP_SECRET=your_app_secret")
        exit(1)
    
    # 토큰 발급
    print("\n1. 접근 토큰 발급 중...")
    token = get_access_token()
    if not token:
        exit(1)
    print(f"✅ 토큰 발급 성공: {token[:20]}...")
    
    # 옵션 월물 리스트 조회
    print("\n2. 옵션 월물 리스트 조회 중...")
    option_list = get_option_list(token)
    
    # 202601 콜풋 전광판 조회
    print("\n3. 202601 콜풋 전광판 조회 중...")
    callput_data = get_callput_board(token, "202601")
    
    # 202512 (12월물)도 비교를 위해 조회
    print("\n4. 202512 콜풋 전광판 조회 중 (비교용)...")
    callput_data_dec = get_callput_board(token, "202512")
    
    print("\n" + "=" * 60)
    print("조회 완료!")
    print("=" * 60)
