# -*- coding: utf-8 -*-
"""
오늘의 만나 — 성경 말씀 음성 일괄 생성 (ElevenLabs)

[사용법]
1) 준비물
   pip install requests
   ffmpeg 설치 (용량 압축용, 권장)
   https://ffmpeg.org/download.html  또는  Windows: winget install ffmpeg

2) API 키를 '환경변수'로 설정 (코드에 직접 넣지 말 것!)
   Windows(PowerShell):  $env:ELEVEN_API_KEY="발급받은키"
   Mac/Linux:            export ELEVEN_API_KEY="발급받은키"

3) 실행

   [테스트 먼저!] 5개만 만들어 목소리를 확인하세요:
   python generate_voice.py --lang ko --limit 5

   [한국어 전체]  index.html 읽어서 voices/ 폴더에 생성
   python generate_voice.py --lang ko

   [영어 전체]    index_en.html 읽어서 voices_en/ 폴더에 생성
   python generate_voice.py --lang en

   * index.html / index_en.html 을 이 스크립트와 같은 폴더에 두세요.
     (프로젝트의 app/src/main/assets/ 안에 있습니다)

[특징]
- 이미 만든 파일은 건너뜀 → 중간에 끊겨도 다시 실행하면 이어서 진행
- 실패한 구절은 failed.txt에 기록 → 나중에 재시도
- 진행상황/예상 비용 실시간 표시
"""

import os, re, json, time, argparse, subprocess, hashlib
import requests

API_URL = "https://api.elevenlabs.io/v1/text-to-speech/{voice_id}"

# 한국어 지원 다국어 모델
MODEL_ID = "eleven_multilingual_v2"

# 목소리 ID (언어별 기본값)
VOICE_KO = "jB1Cifc2UQbq1gR3wnb0"   # 한국어 낭독용
VOICE_EN = "EkK5I93UQWFDigLMpZcX"   # 영어 낭독용


def load_verses(path):
    """index.html에서 const DATA = {...} 추출"""
    h = open(path, encoding='utf-8').read()
    m = re.search(r'const DATA = (\{.*?\});\n', h, re.S)
    if not m:
        raise SystemExit("index.html에서 말씀 데이터를 찾지 못했습니다.")
    D = json.loads(m.group(1))
    out = []
    for cat, obj in D.items():
        for text, ref in obj['verses']:
            out.append((ref, text))
    return out


def ref_to_filename(ref):
    """'요한복음 11:1' -> 'yohan_11_1' 같은 안전한 파일명 (해시로 충돌 방지)"""
    h = hashlib.md5(ref.encode('utf-8')).hexdigest()[:12]
    return f"v_{h}.mp3"


def speak_ref(ref, lang="ko"):
    """'요한복음 11:1' -> '요한복음 11장 1절' (숫자를 시간처럼 읽는 문제 방지)"""
    if lang == "en":
        ref = re.sub(r'(\d+):(\d+)-(\d+)', r'chapter \1, verses \2 to \3', ref)
        ref = re.sub(r'(\d+):(\d+)', r'chapter \1, verse \2', ref)
        return ref
    ref = re.sub(r'(\d+):(\d+)-(\d+)', r'\1장 \2절에서 \3절', ref)
    ref = re.sub(r'(\d+):(\d+)', r'\1장 \2절', ref)
    return ref


def tts(text, api_key, voice_id, retries=3):
    headers = {"xi-api-key": api_key, "Content-Type": "application/json"}
    payload = {
        "text": text,
        "model_id": MODEL_ID,
        "voice_settings": {
            "stability": 0.80,        # 높을수록 차분하고 일정한 톤 (감정 기복 억제)
            "similarity_boost": 0.75,
            "style": 0.0,             # 0 = 연기·억양 과장 없음, 담담한 낭독
            "use_speaker_boost": True,
            "speed": 0.92,            # 0.92 = 살짝 느리게, 묵상하기 좋은 속도
        },

    }
    for attempt in range(retries):
        r = requests.post(API_URL.format(voice_id=voice_id),
                          headers=headers, json=payload, timeout=120)
        if r.status_code == 200:
            return r.content
        if r.status_code == 429:      # 속도 제한
            time.sleep(8 * (attempt + 1)); continue
        if r.status_code == 401:
            raise SystemExit("API 키가 잘못되었습니다. ELEVEN_API_KEY를 확인하세요.")
        time.sleep(3)
    return None


def compress(path, bitrate="32k"):
    """ffmpeg으로 32kbps 모노 압축 (용량 1/3 절감)"""
    tmp = path + ".tmp.mp3"
    try:
        subprocess.run(
            ["ffmpeg", "-y", "-i", path, "-ac", "1", "-b:a", bitrate, tmp],
            check=True, capture_output=True)
        os.replace(tmp, path)
        return True
    except Exception:
        if os.path.exists(tmp):
            os.remove(tmp)
        return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", default="index.html", help="말씀이 든 index.html 경로")
    ap.add_argument("--outdir", default="voices", help="mp3 저장 폴더")
    ap.add_argument("--lang", choices=["ko", "en"], default="ko",
                    help="ko=한국어(index.html), en=영어(index_en.html)")
    ap.add_argument("--voice", default=None, help="목소리 ID 직접 지정(생략 시 --lang 기본값)")
    ap.add_argument("--limit", type=int, default=0, help="테스트용: N개만 생성")
    ap.add_argument("--with-ref", action="store_true",
                    help="본문 뒤에 '요한복음 11장 1절 말씀입니다' 덧붙이기")
    ap.add_argument("--no-compress", action="store_true", help="ffmpeg 압축 건너뛰기")
    args = ap.parse_args()

    if args.voice is None:
        args.voice = VOICE_KO if args.lang == "ko" else VOICE_EN
    if args.input == "index.html" and args.lang == "en":
        args.input = "index_en.html"
    if args.outdir == "voices" and args.lang == "en":
        args.outdir = "voices_en"

    api_key = os.environ.get("ELEVEN_API_KEY")
    if not api_key:
        raise SystemExit(
            "환경변수 ELEVEN_API_KEY가 없습니다.\n"
            '  Windows: $env:ELEVEN_API_KEY="키"\n'
            '  Mac/Linux: export ELEVEN_API_KEY="키"')

    verses = load_verses(args.input)
    if args.limit:
        verses = verses[:args.limit]

    os.makedirs(args.outdir, exist_ok=True)
    manifest_path = os.path.join(args.outdir, "manifest.json")
    manifest = {}
    if os.path.exists(manifest_path):
        manifest = json.load(open(manifest_path, encoding='utf-8'))

    total_chars = sum(len(t) for _, t in verses)
    print(f"대상 구절: {len(verses)}개 / 약 {total_chars:,}자")
    print(f"저장 위치: {os.path.abspath(args.outdir)}\n")

    done, skipped, failed = 0, 0, []
    for i, (ref, text) in enumerate(verses, 1):
        fname = ref_to_filename(ref)
        fpath = os.path.join(args.outdir, fname)

        if os.path.exists(fpath) and os.path.getsize(fpath) > 1000:
            manifest[ref] = fname
            skipped += 1
            continue

        if not args.with_ref:
            say = text
        elif args.lang == "en":
            say = f"{text}. {speak_ref(ref, 'en')}."
        else:
            say = f"{text}. {speak_ref(ref)} 말씀입니다."
        audio = tts(say, api_key, args.voice)

        if audio is None:
            failed.append(ref)
            print(f"[{i}/{len(verses)}] 실패: {ref}")
            continue

        with open(fpath, "wb") as f:
            f.write(audio)
        if not args.no_compress:
            compress(fpath)

        manifest[ref] = fname
        done += 1

        if done % 10 == 0 or i == len(verses):
            size_mb = sum(os.path.getsize(os.path.join(args.outdir, f))
                          for f in os.listdir(args.outdir) if f.endswith(".mp3")) / 1024 / 1024
            print(f"[{i}/{len(verses)}] 생성 {done} · 건너뜀 {skipped} · 실패 {len(failed)} · 누적 {size_mb:.1f}MB")
            json.dump(manifest, open(manifest_path, "w", encoding='utf-8'), ensure_ascii=False)

        time.sleep(0.35)   # 속도 제한 회피

    json.dump(manifest, open(manifest_path, "w", encoding='utf-8'), ensure_ascii=False)
    if failed:
        open(os.path.join(args.outdir, "failed.txt"), "w", encoding='utf-8').write("\n".join(failed))
        print(f"\n실패 {len(failed)}건 → failed.txt 기록됨. 스크립트를 다시 실행하면 재시도합니다.")

    size_mb = sum(os.path.getsize(os.path.join(args.outdir, f))
                  for f in os.listdir(args.outdir) if f.endswith(".mp3")) / 1024 / 1024
    print(f"\n완료 — 파일 {len(manifest)}개 / 총 {size_mb:.1f}MB")
    print("다음 단계: voices 폴더를 app/src/main/assets/voices/ 로 복사하세요.")


if __name__ == "__main__":
    main()
