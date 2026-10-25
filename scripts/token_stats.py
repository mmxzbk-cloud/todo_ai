#!/usr/bin/env python3
"""实验轮次 token 用量统计（配合 docs/superpowers-对比测试方案.md §5.2）。

用法:
    python scripts/token_stats.py <model-io-session_*.jsonl 的路径>

数据源:
    ~/.zcode/cli/rollout/model-io-session_<sessionId>.jsonl
    sessionId 见各轮实验会话（或按修改时间在 rollout 目录中定位）。

口径说明:
    本机 rollout 中 response.usage.inputTokens 已包含缓存读取部分
    （totalTokens = inputTokens + outputTokens），cache_read 是 input 的子集。
    对比时重点看 output（净生成量）与 total 五列分开呈现。
"""
import json
import sys
from collections import Counter
from datetime import datetime


def pick(usage, *names):
    for name in names:
        value = usage.get(name)
        if value:
            return value
    return 0


def parse_ts(value):
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None


def main(path):
    requests = 0
    total_in = total_out = cache_read = cache_write = 0
    models = Counter()
    first_started = last_completed = None

    with open(path, encoding="utf-8") as f:
        for line in f:
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue

            started = parse_ts(rec.get("startedAt"))
            completed = parse_ts(rec.get("completedAt"))
            if started and (first_started is None or started < first_started):
                first_started = started
            if completed and (last_completed is None or completed > last_completed):
                last_completed = completed

            # 权威字段是 response.usage（camelCase）；兼容旧格式与 provider 原始字段
            usage = (rec.get("response") or {}).get("usage") or rec.get("usage") \
                or ((rec.get("response") or {}).get("providerMetadata") or {})
            if isinstance(usage, dict) and "anthropic" in usage:
                usage = usage["anthropic"].get("usage") or {}
            if not usage:
                continue

            requests += 1
            model_id = (rec.get("model") or {}).get("modelId")
            if model_id:
                models[model_id] += 1

            total_in += pick(usage, "inputTokens", "input_tokens")
            total_out += pick(usage, "outputTokens", "output_tokens")
            cache_read += pick(usage, "cacheReadTokens", "cache_read_input_tokens")
            cache_write += pick(usage, "cacheWriteTokens", "cache_creation_input_tokens")

    print(f"file={path}")
    print(f"requests={requests}")
    print(f"input={total_in} output={total_out} cache_read={cache_read} "
          f"cache_write={cache_write} total={total_in + total_out}")
    print(f"models={dict(models)}")
    print(f"first_started={first_started or 'n/a'}")
    print(f"last_completed={last_completed or 'n/a'}")
    if first_started and last_completed:
        print(f"wall_clock={(last_completed - first_started)}")

    if len(models) > 1:
        print("WARNING: 会话内出现多个 modelId，按方案 §5.2 公平性规则该轮作废重跑")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1])
