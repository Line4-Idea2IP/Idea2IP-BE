"""Real-model regression check for the AI interview.

Drives a running backend through the public API with fixed scenarios, then scores each run against the
scenario's expectations: which fields must be asked or skipped, final field states, HTTP failures,
repeated questions, idempotent replay, quotes that must be the user's own words, and fields settled from
another field's sentence. Standard library only.

Usage (see README.md):
    python -I scripts/interview-eval/run_eval.py --base-url http://127.0.0.1:18081 --runs 3
"""
import argparse
import json
import re
import secrets
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib
from datetime import datetime
from pathlib import Path

FIELDS = ["AI_TOOL", "AI_PURPOSE", "AI_OUTPUT_USAGE", "HUMAN_CREATED_ELEMENT", "HUMAN_EDITED_EXPRESSION"]
FIELD_LABELS = {
    "AI_TOOL": "도구", "AI_PURPOSE": "목적", "AI_OUTPUT_USAGE": "활용",
    "HUMAN_CREATED_ELEMENT": "직접 창작", "HUMAN_EDITED_EXPRESSION": "직접 수정",
}
MAX_ASKS_PER_FIELD = 3
MAX_TURNS = 16
REQUEST_TIMEOUT = 360
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}
NON_WORD = re.compile(r"[^\w]", re.UNICODE)
# A question quotes the user as "‘…’라고 하셨는데"; other quoted text is a reply hint such as ‘없음’.
QUOTED = re.compile(r"[‘'“\"]([^’'”\"]{2,})[’'”\"]\s*(?:이)?라고 하셨")
# Lead-in the backend puts before a question (fields it already confirmed, a move to the user's own work);
# repeated questions are compared without it.
LEAD_IN = re.compile(r"^(?:(?:말씀하신 내용으로|작품 설명에서) [^.?!]{1,160}까지 확인했습니다\. )?"
                     r"(?:이제 직접 작업하신 부분을 여쭤볼게요\. |조금만 더 여쭤볼게요\. )?")

CHECK_LABELS = {
    "http_ok": "모든 요청 첫 시도 성공 (503 포함 오류 0건)",
    "completed": "인터뷰 완료",
    "must_ask": "물어야 할 항목을 물음",
    "must_skip": "생략해야 할 항목을 묻지 않음",
    "min_asks": "다시 물어야 할 항목을 다시 물음",
    "ask_limit": f"한 항목 최대 {MAX_ASKS_PER_FIELD}회",
    "final_states": "최종 항목 상태",
    "readiness_states": "시작 전 항목 상태",
    "no_repeated_question": "같은 질문 반복 없음 (공백·문장부호 무시)",
    "idempotent_replay": "같은 요청 ID 재전송 시 중복 저장 없음",
    "verbatim_quotes": "질문 속 인용은 사용자 원문",
    "settled_grounded": "자동 완료 근거는 사용자 원문이고 다른 항목과 겹치지 않음",
}


class ApiError(Exception):
    def __init__(self, method, path, status, body):
        super().__init__(f"{method} {path} -> HTTP {status}: {body}")
        self.status = status
        self.body = body


class Client:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.token = None

    def request(self, method, path, body=None, files=None):
        headers = {"Accept": "application/json"}
        data = None
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif files is not None:
            data, content_type = multipart(files)
            headers["Content-Type"] = content_type
        req = urllib.request.Request(self.base_url + path, data=data, method=method, headers=headers)
        started = time.monotonic()
        try:
            with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        elapsed = time.monotonic() - started
        try:
            payload = json.loads(raw.decode("utf-8")) if raw else None
        except (UnicodeDecodeError, json.JSONDecodeError):
            payload = raw.decode("utf-8", "replace")
        return status, payload, elapsed

    def ok(self, method, path, body=None, files=None, allowed=(200, 201)):
        status, payload, _ = self.request(method, path, body, files)
        if status not in allowed:
            raise ApiError(method, path, status, payload)
        return payload.get("data") if isinstance(payload, dict) else payload


def multipart(files):
    boundary = uuid.uuid4().hex
    parts = []
    for field, (filename, content, content_type) in files.items():
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; "
                     f"filename=\"{filename}\"\r\nContent-Type: {content_type}\r\n\r\n".encode("utf-8"))
        parts.append(content)
        parts.append(b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def test_png(size=48):
    """A small solid-color PNG; the interview reads material text, not pixels."""
    row = b"\x00" + bytes([200, 60, 50]) * size
    raw = row * size

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def normalized(text):
    return NON_WORD.sub("", text or "").replace("_", "")


def merged(defaults, scenario):
    result = dict(scenario)
    for key in ("project", "artwork", "materials"):
        result.setdefault(key, defaults[key])
    answers = dict(defaults["answers"])
    answers.update(scenario.get("answers", {}))
    result["answers"] = answers
    return result


def run_scenario(base_url, scenario, image):
    """Runs one interview and returns its transcript; scoring happens separately."""
    client = Client(base_url)
    credentials = {"loginId": f"eval-{uuid.uuid4().hex[:20]}", "password": secrets.token_urlsafe(18) + "!aA7"}
    client.ok("POST", "/api/auth/signup", credentials)
    client.token = client.ok("POST", "/api/auth/login", credentials)["accessToken"]

    project = client.ok("POST", "/api/projects", {**scenario["project"],
                                                  "title": f"[eval {scenario['id']}] {scenario['project']['title']}"})
    project_path = f"/api/projects/{project['projectId']}"
    client.ok("POST", f"{project_path}/artwork", scenario["artwork"])
    client.ok("PATCH", f"{project_path}/artwork/image", files={"file": ("artwork.png", image, "image/png")})
    for material in scenario["materials"]:
        registered = client.ok("POST", f"{project_path}/materials", material)
        client.ok("POST", f"/api/materials/{registered['materialId']}/files",
                  files={"files": ("material.png", image, "image/png")})

    readiness = client.ok("GET", f"{project_path}/interview/readiness")
    transcript = {"scenario": scenario["id"], "projectId": project["projectId"], "readiness": readiness,
                  "turns": [], "errors": [], "replay": None}
    if not readiness.get("canStart"):
        transcript["errors"].append({"step": "readiness", "detail": "canStart=false"})
        return transcript

    interview_path = f"{project_path}/interview"
    status, payload, elapsed = client.request("POST", interview_path)
    transcript["start"] = {"status": status, "seconds": round(elapsed, 1)}
    if status not in (200, 201):
        transcript["errors"].append({"step": "start", "status": status, "body": payload})
        return transcript
    interview = payload["data"]
    asked = {}
    for index in range(MAX_TURNS):
        if interview.get("status") == "COMPLETED":
            break
        question = interview.get("currentQuestion")
        if not question:
            transcript["errors"].append({"step": f"turn {index}", "detail": "no current question"})
            break
        field = question["targetField"]
        choices = scenario["answers"].get(field) or ["없습니다."]
        answer = choices[min(asked.get(field, 0), len(choices) - 1)]
        asked[field] = asked.get(field, 0) + 1
        request_body = {"questionId": question["messageId"], "content": answer, "requestId": str(uuid.uuid4())}
        status, payload, elapsed = client.request("POST", f"{interview_path}/answers", request_body)
        turn = {"field": field, "question": question["content"], "reason": question.get("reason"),
                "materialRefs": question.get("materialRefs"), "answer": answer, "status": status,
                "seconds": round(elapsed, 1)}
        transcript["turns"].append(turn)
        print(f"    {FIELD_LABELS[field]:<6} HTTP {status} {elapsed:5.1f}s  Q: {question['content'][:70]}")
        if status != 200:
            turn["error"] = payload
            # The answer is stored before question generation, so the documented recovery is the same request again.
            status, payload, elapsed = client.request("POST", f"{interview_path}/answers", request_body)
            turn["retryStatus"] = status
            if status != 200:
                transcript["errors"].append({"step": f"answer {index}", "status": status, "body": payload})
                break
        result = payload["data"]
        if transcript["replay"] is None:
            r_status, r_payload, _ = client.request("POST", f"{interview_path}/answers", request_body)
            replayed = r_payload.get("data") if isinstance(r_payload, dict) else None
            next_id = (result["interview"].get("currentQuestion") or {}).get("messageId")
            transcript["replay"] = {
                "status": r_status,
                "sameAnswer": bool(replayed) and replayed.get("answerMessageId") == result["answerMessageId"],
                "sameNextQuestion": bool(replayed)
                and (replayed["interview"].get("currentQuestion") or {}).get("messageId") == next_id,
            }
        interview = result["interview"]
    transcript["final"] = interview
    transcript["messages"] = client.ok("GET", f"{interview_path}/messages").get("messages", [])
    return transcript


def score(scenario, transcript):
    """Returns {check: (passed, detail)}; a check that does not apply to the scenario is left out."""
    turns = transcript["turns"]
    answers = [turn["answer"] for turn in turns]
    asks = {field: sum(1 for turn in turns if turn["field"] == field) for field in FIELDS}
    final = {state["field"]: state for state in (transcript.get("final") or {}).get("fieldStates", [])}
    checks = {}

    failures = [t for t in turns if t["status"] != 200]
    start_failed = transcript.get("start", {}).get("status") not in (200, 201)
    checks["http_ok"] = (not failures and not start_failed and not transcript["errors"],
                         ", ".join(f"{t['field']} HTTP {t['status']}" for t in failures)
                         or "; ".join(str(e.get("step")) + " " + str(e.get("status", e.get("detail", "")))
                                      for e in transcript["errors"]))
    status = (transcript.get("final") or {}).get("status")
    checks["completed"] = (status == "COMPLETED", f"status={status}")
    missing = [f for f in scenario.get("mustAsk", []) if asks[f] == 0]
    if scenario.get("mustAsk"):
        checks["must_ask"] = (not missing, "묻지 않음: " + ", ".join(missing) if missing else "")
    skipped_wrong = [f for f in scenario.get("mustSkip", []) if asks[f] > 0]
    if scenario.get("mustSkip"):
        checks["must_skip"] = (not skipped_wrong, "물음: " + ", ".join(skipped_wrong) if skipped_wrong else "")
    if scenario.get("minAsks"):
        short = [f"{f} {asks[f]}/{n}" for f, n in scenario["minAsks"].items() if asks[f] < n]
        checks["min_asks"] = (not short, ", ".join(short))
    over = [f"{f} {n}회" for f, n in asks.items() if n > MAX_ASKS_PER_FIELD]
    checks["ask_limit"] = (not over, ", ".join(over))
    if scenario.get("expectFinal"):
        wrong = [f"{f}={final.get(f, {}).get('status')}" for f, allowed in scenario["expectFinal"].items()
                 if final.get(f, {}).get("status") not in allowed]
        checks["final_states"] = (not wrong, ", ".join(wrong))
    if scenario.get("expectReadiness"):
        before = {s["field"]: s["status"] for s in transcript["readiness"].get("fieldStates", [])}
        wrong = [f"{f}={before.get(f)}" for f, allowed in scenario["expectReadiness"].items()
                 if before.get(f) not in allowed]
        checks["readiness_states"] = (not wrong, ", ".join(wrong))

    seen, repeats = {}, []
    for turn in turns:
        key = normalized(LEAD_IN.sub("", turn["question"], count=1))
        if key in seen:
            repeats.append(f"{seen[key]}·{turn['field']}: {turn['question'][:40]}")
        seen.setdefault(key, turn["field"])
    checks["no_repeated_question"] = (not repeats, " / ".join(repeats))
    replay = transcript.get("replay")
    if replay:
        checks["idempotent_replay"] = (replay["status"] == 200 and replay["sameAnswer"] and replay["sameNextQuestion"],
                                       json.dumps(replay, ensure_ascii=False))

    invented = []
    for index, turn in enumerate(turns):
        earlier = " ".join(answers[:index])
        for quote in QUOTED.findall(turn["question"]):
            if normalized(quote) and normalized(quote) not in normalized(earlier):
                invented.append(f"{turn['field']}: ‘{quote[:30]}’")
    checks["verbatim_quotes"] = (not invented, " / ".join(invented))

    ungrounded = []
    for field in FIELDS[1:]:
        state = final.get(field, {})
        summary = (state.get("summary") or "").strip()
        if asks[field] or state.get("status") != "COMPLETED" or not summary:
            continue
        # Evidence may come from the user's answers or from the project and artwork descriptions they wrote.
        written = answers + [scenario["project"].get("description", ""), scenario["artwork"].get("description", "")]
        if not any(summary in text for text in written):
            ungrounded.append(f"{field} 근거가 답변·설명에 없음")
        for other in FIELDS[1:]:
            other_summary = (final.get(other, {}).get("summary") or "").strip()
            if other != field and other_summary and (summary in other_summary or other_summary in summary):
                ungrounded.append("·".join(sorted([field, other])) + " 같은 문장")
    if any(asks[f] == 0 and final.get(f, {}).get("status") == "COMPLETED" for f in FIELDS[1:]):
        checks["settled_grounded"] = (not ungrounded, ", ".join(sorted(set(ungrounded))))
    return checks


def write_summary(out_dir, results, required_phase):
    lines = [f"# 인터뷰 회귀 검사 결과", "", f"- 실행 시각: {datetime.now():%Y-%m-%d %H:%M}",
             f"- 필수 단계: phase ≤ {required_phase} (그보다 뒤 단계 시나리오는 ‘예정’으로 표시)", ""]
    lines += ["| 시나리오 | 단계 | 통과 | 실패한 검사 |", "|---|---|---|---|"]
    failed_required = False
    for result in results:
        runs = result["runs"]
        passed = sum(1 for run in runs if all(ok for ok, _ in run["checks"].values()))
        failing = {}
        for run in runs:
            for name, (ok, _) in run["checks"].items():
                if not ok:
                    failing[name] = failing.get(name, 0) + 1
        required = result["phase"] <= required_phase
        failed_required |= required and passed < len(runs)
        tag = "" if required else " (예정)"
        failing_text = ", ".join(f"{CHECK_LABELS[n]} {c}/{len(runs)}" for n, c in failing.items()) or "—"
        lines.append(f"| {result['id']} | {result['phase']}{tag} | {passed}/{len(runs)} | {failing_text} |")
    lines += ["", "## 실행별 상세", ""]
    for result in results:
        lines.append(f"### {result['id']} — {result['title']}")
        for number, run in enumerate(result["runs"], 1):
            asked = " → ".join(FIELD_LABELS[t["field"]] for t in run["transcript"]["turns"]) or "질문 없음"
            lines.append(f"- 실행 {number}: 질문 {len(run['transcript']['turns'])}개 ({asked})")
            for name, (ok, detail) in run["checks"].items():
                if not ok:
                    lines.append(f"  - 실패 · {CHECK_LABELS[name]}: {detail}")
        lines.append("")
    (out_dir / "summary.md").write_text("\n".join(lines), encoding="utf-8")
    (out_dir / "summary.json").write_text(json.dumps([
        {"id": r["id"], "phase": r["phase"],
         "runs": [{"checks": {k: {"passed": ok, "detail": d} for k, (ok, d) in run["checks"].items()},
                   "transcript": f"{r['id']}-run{n}.json"} for n, run in enumerate(r["runs"], 1)]}
        for r in results], ensure_ascii=False, indent=2), encoding="utf-8")
    return failed_required, "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="AI 인터뷰 실제 모델 회귀 검사")
    parser.add_argument("--base-url", default="http://127.0.0.1:18081")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--scenario", action="append", help="실행할 시나리오 ID (여러 번 지정 가능)")
    parser.add_argument("--phase", type=int, default=0, help="이 단계까지의 시나리오가 실패하면 종료 코드 1")
    parser.add_argument("--scenarios", default=str(Path(__file__).with_name("scenarios.json")))
    parser.add_argument("--image", help="첨부할 PNG/JPEG 경로 (기본: 생성한 단색 PNG)")
    parser.add_argument("--out", help="결과 폴더 (기본: build/interview-eval/<시각>)")
    parser.add_argument("--allow-remote", action="store_true", help="localhost가 아닌 서버에 테스트 계정·프로젝트를 만듦")
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="replace")

    host = urllib.parse.urlparse(args.base_url).hostname
    if host not in LOCAL_HOSTS and not args.allow_remote:
        sys.exit(f"{host}는 로컬 서버가 아닙니다. 테스트 계정과 프로젝트가 생성되므로 --allow-remote를 함께 지정해야 합니다.")
    spec = json.loads(Path(args.scenarios).read_text(encoding="utf-8"))
    scenarios = [merged(spec["defaults"], s) for s in spec["scenarios"]
                 if not args.scenario or s["id"] in args.scenario]
    if not scenarios:
        sys.exit("실행할 시나리오가 없습니다.")
    image = Path(args.image).read_bytes() if args.image else test_png()
    repo = Path(__file__).resolve().parents[2]
    out_dir = Path(args.out) if args.out else repo / "build" / "interview-eval" / datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir.mkdir(parents=True, exist_ok=True)
    print(f"결과 폴더: {out_dir}")

    results = []
    for scenario in scenarios:
        result = {"id": scenario["id"], "title": scenario["title"], "phase": scenario.get("phase", 0), "runs": []}
        for number in range(1, args.runs + 1):
            print(f"[{scenario['id']}] 실행 {number}/{args.runs}")
            try:
                transcript = run_scenario(args.base_url, scenario, image)
            except (ApiError, urllib.error.URLError, OSError, KeyError, TypeError) as error:
                transcript = {"scenario": scenario["id"], "turns": [], "readiness": {}, "replay": None,
                              "errors": [{"step": "setup", "detail": str(error)[:500]}]}
            (out_dir / f"{scenario['id']}-run{number}.json").write_text(
                json.dumps(transcript, ensure_ascii=False, indent=2), encoding="utf-8")
            checks = score(scenario, transcript)
            failed = [CHECK_LABELS[n] for n, (ok, _) in checks.items() if not ok]
            print(f"  → {'통과' if not failed else '실패: ' + ', '.join(failed)}")
            result["runs"].append({"transcript": transcript, "checks": checks})
        results.append(result)

    failed_required, text = write_summary(out_dir, results, args.phase)
    print()
    print(text)
    sys.exit(1 if failed_required else 0)


if __name__ == "__main__":
    main()
