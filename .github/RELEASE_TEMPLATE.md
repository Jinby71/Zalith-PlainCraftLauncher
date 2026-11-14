# 发布流程（只发源码，不发安装包）

本仓库只承载源代码：**不建带 APK 附件的 GitHub Release，也不在仓库里放安装包**。
分发的 APK 由官网（`url_home` 指向的站点）提供；源码始终可以从本仓库取得。

下面把 `1.5.3` / `200075` 换成当次的版本号与 versionCode。

---

## 1. 改版本号

| 位置 | 字段 |
|---|---|
| `ZalithLauncher/gradle.properties` | `launcher_version_code` / `launcher_version_name` |
| `Zalith-PlainCraftLauncher-Release/ZalithLauncher/gradle.properties` | 同上（两条产线都要改） |
| `latest_version_md.json` | `code` / `version` / `default_body` / `bodies` |

versionCode 只增不减；`launcher_name` 与 `CLIENT_TOKEN_SEED` 不要动。

## 2. 写说明

正文以 `latest_version_md.json` 的 `default_body.markdown` 为准（应用内「检查更新」显示的就是它），
再生成一份同源归档到 `release/<版本>.md`：

```bash
python - <<'PY'
import io, json
d = json.load(io.open("latest_version_md.json", encoding="utf-8"))
body = (d.get("default_body") or {}).get("markdown", "").strip()
head = ("# {v}\n\n**versionCode** `{c}` · **包名** `com.zalith.pcl` · **minSdk** 26 · **targetSdk** 34\n"
        "**名称** ZPCL Launcher · **非官方修改版，与 Zalith Launcher 官方无关联**\n\n---\n\n").format(
    v=d["version"], c=d["code"])
io.open("release/%s.md" % d["version"], "w", encoding="utf-8", newline="\n").write(head + body + "\n")
PY
```

## 3. 提交并推源码

```bash
git add -A
git commit -m "1.5.3：<一句话说明>"
git -c http.sslBackend=schannel -c http.schannelCheckRevoke=false -c http.schannelUseSSLCAInfo=false push origin main
```

## 4. 打标签（可选，只标记源码节点）

```bash
git tag -a v1.5.3 -m "ZPCL Launcher 1.5.3 (200075)"
git push origin v1.5.3
```

标签不会触发任何打包上传：仓库里没有 `release` 事件的工作流（`release_ci.yml` 已移除）。

---

## 发布后核对

| 检查 | 期望 |
|---|---|
| 仓库 Releases | 没有带 APK 的发布项 |
| 仓库根目录 | 没有 `*.apk` |
| 应用内检查更新 | 读到 `latest_version_md.json` 的 `code` / `version` |
| 关于页 | 显示 ZPCL Launcher 1.5.3 与「非官方修改版」标注 |
| 源码入口 | 关于页可打开本仓库 |
