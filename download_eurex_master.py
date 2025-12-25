'''EUREX 야간옵션 마스터 파일 다운로드 및 분석'''
import pandas as pd
import urllib.request
import ssl
import zipfile
import os

base_dir = os.getcwd()

# SSL 인증 우회
ssl._create_default_https_context = ssl._create_unverified_context

# 마스터 파일 다운로드
print("Downloading fo_eurex_code.mst.zip...")
urllib.request.urlretrieve(
    "https://new.real.download.dws.co.kr/common/master/fo_eurex_code.mst.zip",
    base_dir + "\\fo_eurex_code.mst.zip"
)

# 압축 해제
print("Extracting...")
fo_eurex_code_zip = zipfile.ZipFile('fo_eurex_code.mst.zip')
fo_eurex_code_zip.extractall()
fo_eurex_code_zip.close()

file_name = base_dir + "\\fo_eurex_code.mst"

# 파일 파싱 (한투 공식 로직)
tmp_fil1 = base_dir + "\\fo_eurex_code_part1.tmp"
tmp_fil2 = base_dir + "\\fo_eurex_code_part2.tmp"

wf1 = open(tmp_fil1, mode="w", encoding="utf-8")
wf2 = open(tmp_fil2, mode="w", encoding="utf-8")

with open(file_name, mode="r", encoding="cp949") as f:
    for row in f:
        rf1 = row[0:59]
        rf1_1 = rf1[0:1]        # 상품종류
        rf1_2 = rf1[1:10]       # 단축코드
        rf1_3 = rf1[10:22].strip()  # 표준코드
        rf1_4 = rf1[22:59].strip()  # 한글종목명
        wf1.write(rf1_1 + ',' + rf1_2 + ',' + rf1_3 + ',' + rf1_4 + '\n')
        
        rf2 = row[59:].lstrip()
        wf2.write(rf2)

wf1.close()
wf2.close()

# DataFrame 생성
part1_columns = ['상품종류','단축코드','표준코드','한글종목명']
df1 = pd.read_csv(tmp_fil1, header=None, names=part1_columns, encoding='utf-8')

# 2026년 1월 만기 옵션 코드만 필터링
print("\n=== 2026년 1월 KOSPI200 야간옵션 코드 (행사가 570~600) ===")
df_filtered = df1[
    (df1['한글종목명'].str.contains('KOSPI 200', na=False)) &
    (df1['한글종목명'].str.contains('202601', na=False)) &
    (df1['한글종목명'].str.contains('5[7-9][0-9]|60[0-9]', na=False, regex=True))
]

print(df_filtered[['단축코드', '표준코드', '한글종목명']].head(20).to_string())

print(f"\n총 {len(df_filtered)}개 종목 발견")

# 엑셀로 전체 저장
df1.to_excel('fo_eurex_code_full.xlsx', index=False)
print("\n전체 데이터를 fo_eurex_code_full.xlsx로 저장했습니다.")
