import os
import shutil

ROOT = r"D:\MCModDev\forge-mdk"
NEW = "yanscriptures"
OLD = "examplemod"
PKG_GUARD = "__PKGPATH__"


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


res_root = os.path.join(ROOT, "src", "main", "resources")

# ---------- 1. 目录改名：复制 + 删除（rename 可能被占用锁拒绝） ----------
for base in ("assets", "data"):
    old_dir = os.path.join(res_root, base, OLD)
    new_dir = os.path.join(res_root, base, NEW)
    if os.path.isdir(new_dir):
        shutil.rmtree(new_dir)
    if os.path.isdir(old_dir):
        shutil.copytree(old_dir, new_dir)
        try:
            shutil.rmtree(old_dir)
            print(f"[dir] {base}/{OLD} -> {base}/{NEW}（已移动）")
        except Exception as e:
            print(f"[dir] {base}/{OLD} -> {base}/{NEW}（已复制，旧目录删除失败: {e}）")

# ---------- 2. 资源内容 ----------
changed_res = 0
for dirpath, _, filenames in os.walk(res_root):
    for name in filenames:
        if not name.endswith((".json", ".mcmeta", ".toml", ".txt", ".cfg")):
            continue
        path = os.path.join(dirpath, name)
        try:
            text = read(path)
        except UnicodeDecodeError:
            continue
        if OLD in text:
            write(path, text.replace(OLD, NEW))
            changed_res += 1

# ---------- 3. Java ----------
changed_java = 0
pkg_path = f"com.example.{OLD}"
for dirpath, _, filenames in os.walk(os.path.join(ROOT, "src", "main", "java")):
    for name in filenames:
        if not name.endswith(".java"):
            continue
        path = os.path.join(dirpath, name)
        text = read(path)
        if OLD not in text:
            continue
        guarded = text.replace(pkg_path, PKG_GUARD).replace(OLD, NEW).replace(PKG_GUARD, pkg_path)
        if guarded != text:
            write(path, guarded)
            changed_java += 1

# ---------- 4. gradle.properties ----------
gp = os.path.join(ROOT, "gradle.properties")
text = read(gp)
if f"mod_id={OLD}" in text:
    text = text.replace(f"mod_id={OLD}", f"mod_id={NEW}")
    text = text.replace("mod_name=Example Mod", "mod_name=Yan's Scriptures")
    write(gp, text)
    print("[gradle.properties] mod_id / mod_name 已更新")

# ---------- 5. 删除悬空 mixin 配置 ----------
for fn in (f"{OLD}.mixins.json", f"{NEW}.mixins.json"):
    p = os.path.join(res_root, fn)
    if os.path.exists(p):
        try:
            os.remove(p)
            print(f"[del] {fn}")
        except Exception as e:
            print(f"[del] {fn} 失败: {e}")

print(f"资源 {changed_res} 个，Java {changed_java} 个")
