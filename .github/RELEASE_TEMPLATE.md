# 正式版发布模板

发布一个正式版时，按下面四步走。把 `1.4.6` / `200063` 换成当次的版本号与 versionCode。

---

## 1. 打标签

```bash
cd D:/workpls/Zalith-PlainCraftLauncher
git tag -a v1.4.6 -m "Zalith-Plain Craft Launcher 1.4.6 (200063)"
git push origin main --tags
```

> `release_ci.yml` 监听的是 **release published**，不是 tag。只推 tag 不会触发构建，第 3 步发布才会。

## 2. 准备正文

正文直接用仓库里的 `release/<版本>.md`（由 `latest_version_md.json` 的 `default_body.markdown` 生成，
与应用内「检查更新」显示的说明保持一致）。没有就先生成：

```bash
python - <<'PY'
import io, json
d = json.load(io.open("latest_version_md.json", encoding="utf-8"))
body = (d.get("default_body") or {}).get("markdown", "").strip()
head = f"# {d['version']}\n\n**Version code** `{d['code']}`  ·  **Package** `com.zalith.pcl`\n\n---\n\n"
io.open(f"release/{d['version']}.md", "w", encoding="utf-8", newline="\n").write(head + body + "\n")
PY
```

## 3. 建 Release（草稿 → 审核 → 发布）

```bash
gh release create v1.4.6 \
  --draft \
  --title "1.4.6" \
  --notes-file release/1.4.6.md \
  outputs/z-pcl-1.4.6-plugin-upgrade/ZalithLauncher-1.4.6-arm64-v8a-release.apk
```

- `--draft`：先存草稿，附件已挂好，点 Publish 才会触发 CI 重编并补传全部 ABI。
- 附件至少要挂 **release 版** 的 APK；CI 会把 `apks/*.apk` 全量补上（`fail_on_unmatched_files: true`，别让它找不到文件）。

## 4. 发布后核对

| 检查 | 期望 |
|---|---|
| Release 附件 | 至少一个 `ZalithLauncher-<版本>-*.apk` |
| 应用内检查更新 | 读到 `latest_version_md.json` 的 `code` / `version` |
| 标签 | `v1.4.6` 指向发布所用提交 |
| 签名 | 与 `zalith_release.jks` 一致（包名 `com.zalith.pcl`） |

---

## 每版必须同步的三处

1. `ZalithLauncher/gradle.properties`（`launcher_version_code` / `launcher_version_name`）
2. `Zalith-PlainCraftLauncher-Release/` 同名字段
3. `latest_version_md.json`（两条线各一份）

versionCode 只增不减；`launcher_name` 与 `CLIENT_TOKEN_SEED` 不要动。
