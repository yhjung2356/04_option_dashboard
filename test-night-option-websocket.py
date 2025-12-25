#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
야간옵션 WebSocket 종목코드 형식 테스트
다양한 형식의 종목코드로 구독을 시도하여 어떤 형식이 작동하는지 확인
"""

import websocket
import json
import time
import requests
from datetime import datetime

# KIS API 설정 (application.properties에서 가져온 값)
APP_KEY = "PSjWUtUXbcOk29l4dHhBpqgNT0kOj07pIbQp"
APP_SECRET = "vQQXAQ+0I4aFB5kZRyG6UbhgVCvvYnBK6ZFDWBqFf11zD5JVxsEKZwYQFaVGw/mTXQeOgmD2EL7QMOa7KJoZKK+v2e2A24nLZkXZl+b2QE/V/Q5GI7p1ztbMpQQYfRv7qJHDqe8jCaKdZIJlIZ8S13o3E3dBY+4gGVBGO2YwKmWXzjmmZ1s="
BASE_URL = "https://openapi.koreainvestment.com:9443"

def get_approval_key():
    """WebSocket 접속키 발급"""
    url = f"{BASE_URL}/oauth2/Approval"
    headers = {
        "Content-Type": "application/json"
    }
    body = {
        "grant_type": "client_credentials",
        "appkey": APP_KEY,
        "secretkey": APP_SECRET
    }
    
    try:
        response = requests.post(url, headers=headers, json=body)
        if response.status_code == 200:
            approval_key = response.json()["approval_key"]
            print(f"✅ Approval Key 발급 성공: {approval_key[:20]}...")
            return approval_key
        else:
            print(f"❌ Approval Key 발급 실패: {response.status_code}")
            print(response.text)
            return None
    except Exception as e:
        print(f"❌ 에러: {e}")
        return None

def test_option_code(approval_key, option_code, tr_id="H0EUASP0"):
    """특정 종목코드로 WebSocket 구독 테스트"""
    
    ws_url = "ws://ops.koreainvestment.com:21000"
    result = {"code": option_code, "success": False, "response": None}
    
    def on_open(ws):
        # 구독 메시지 전송
        subscribe_msg = {
            "header": {
                "tr_type": "1",
                "content-type": "utf-8",
                "approval_key": approval_key,
                "custtype": "P"
            },
            "body": {
                "input": {
                    "tr_id": tr_id,
                    "tr_key": option_code
                }
            }
        }
        print(f"\n🔄 테스트 중: {option_code} (TR_ID: {tr_id})")
        print(f"   전송: {json.dumps(subscribe_msg, ensure_ascii=False)}")
        ws.send(json.dumps(subscribe_msg))
    
    def on_message(ws, message):
        try:
            response = json.loads(message)
            result["response"] = response
            
            rt_cd = response.get("body", {}).get("rt_cd", "")
            msg_cd = response.get("body", {}).get("msg_cd", "")
            msg1 = response.get("body", {}).get("msg1", "")
            
            if rt_cd == "0" or msg_cd == "OPSP8996":  # 성공 또는 이미 사용중
                result["success"] = True
                print(f"   ✅ 성공: {msg_cd} - {msg1}")
            else:
                print(f"   ❌ 실패: {msg_cd} - {msg1}")
            
            ws.close()
        except Exception as e:
            print(f"   ⚠️ 메시지 파싱 에러: {e}")
            ws.close()
    
    def on_error(ws, error):
        print(f"   ⚠️ WebSocket 에러: {error}")
    
    def on_close(ws, close_status_code, close_msg):
        pass
    
    try:
        ws = websocket.WebSocketApp(
            ws_url,
            on_open=on_open,
            on_message=on_message,
            on_error=on_error,
            on_close=on_close
        )
        ws.run_forever()
        time.sleep(0.5)  # 다음 테스트 전 대기
    except Exception as e:
        print(f"   ❌ 연결 실패: {e}")
    
    return result

def main():
    print("=" * 70)
    print("야간옵션 WebSocket 종목코드 형식 테스트")
    print(f"시작 시간: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    
    # Approval Key 발급
    approval_key = get_approval_key()
    if not approval_key:
        print("❌ Approval Key 발급 실패. 종료합니다.")
        return
    
    print("\n" + "=" * 70)
    print("테스트할 종목코드 형식")
    print("=" * 70)
    
    test_codes = [
        # 공식 예제 코드 (101W9000 형식)
        ("공식예제", "B01601580"),      # 예제에서 직접 발견
       
    ]
    
    results = []
    
    for category, code in test_codes:
        result = test_option_code(approval_key, code)
        results.append((category, result))
        time.sleep(0.3)  # API 호출 간격
    
    # 결과 요약
    print("\n" + "=" * 70)
    print("테스트 결과 요약")
    print("=" * 70)
    
    success_codes = []
    failed_codes = []
    
    for category, result in results:
        code = result["code"]
        if result["success"]:
            success_codes.append((category, code))
            print(f"✅ [{category}] {code} - 성공")
        else:
            failed_codes.append((category, code))
            response = result.get("response", {})
            msg_cd = response.get("body", {}).get("msg_cd", "N/A")
            msg1 = response.get("body", {}).get("msg1", "N/A")
            print(f"❌ [{category}] {code} - 실패: {msg_cd} - {msg1}")
    
    print("\n" + "=" * 70)
    print("최종 분석")
    print("=" * 70)
    print(f"성공: {len(success_codes)}개")
    print(f"실패: {len(failed_codes)}개")
    
    if success_codes:
        print("\n✨ 작동하는 코드 형식:")
        for category, code in success_codes:
            print(f"   - [{category}] {code}")
    else:
        print("\n⚠️ 작동하는 코드가 없습니다.")
        print("   가능한 원인:")
        print("   1. 야간장 옵션 실시간 데이터가 지원되지 않음")
        print("   2. 특정 권한 또는 계약이 필요함")
        print("   3. 현재 거래 중인 종목이 테스트한 코드와 다름")
    
    print("\n" + "=" * 70)
    print(f"종료 시간: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

if __name__ == "__main__":
    main()
