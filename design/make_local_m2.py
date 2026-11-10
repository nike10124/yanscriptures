"""把 libs/ 里的模组 jar 包装成本地 Maven 仓库，供 fg.deobf 使用。

ForgeGradle 的 fg.deobf 只对「模块依赖」做重映射，拒绝处理 files(...) 文件集合
（报错原文：files(...) dependencies are not deobfuscated）。
所以这里按 Modrinth maven 的目录布局生成 groupId=maven.modrinth 的本地仓库，
build.gradle 里就能继续用同样的坐标。
"""
import os
import shutil

ROOT = 'libs-m2/maven'
GROUP_DIR = os.path.join(ROOT, 'maven', 'modrinth')

# (artifactId, version, 源文件名)
ITEMS = [
    ('goety',                'D3VzEoO9', 'goety-2.5.59.0.jar'),
    ('goety-revelation',     't59lWC8J', 'GoetyRevelation-2.3.5.jar'),
    ('endinglibrary',        'MHYrjSc5', 'EndingLibrary-1.20.1-2.2.1fix-all.jar'),
    ('patchouli',            '94dtOLgZ', 'Patchouli-1.20.1-85-FORGE.jar'),
    ('caelus',               'mRry0DgY', 'caelus-forge-3.2.0+1.20.1.jar'),
    ('moonlight',            'b1cuadwV', 'moonlight-1.20-2.16.37-forge.jar'),
    # 假人（dummmmmmy）已停用：jar 缺 refmap，在 official 映射的开发环境里 mixin 必然
    # InvalidInjectionException。jar 还留在 libs/ 里当资料，但不进构建。
    # ('mmmmmmmmmmmm',       'c1HMqDvI', 'dummmmmmy-1.20-2.0.12-forge.jar'),
    ('enigmatic-legacy',     'yUuDfmnk', 'EnigmaticLegacy-2.30.1.jar'),
    # —— 开发辅助与配方页（2026-10-28）——
    # Curios 用「-」而不是 jar 名里的「+」，因为 Gradle 会把带「+」的版本号当动态版本。
    ('curios-forge',         '5.14.1-1.20.1', 'curios-forge-5.14.1+1.20.1.jar'),
    ('jei',                  '15.74.0.229',   'jei-1.20.1-forge-15.74.0.229.jar'),
    ('jade',                 '11.13.3',       'Jade-1.20.1-Forge-11.13.3.jar'),
    # 星月遗物：CurseForge 独占，这里借用同一个 groupId 走本地仓库
    ('celestial-artifacts',  '1.6.5',    'celestial_artifacts-1.6.5.jar'),
    ('celestial-core',       '1.6.1',    'celestial_core-1.6.1.jar'),
]

POM = '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>maven.modrinth</groupId>
  <artifactId>{artifact}</artifactId>
  <version>{version}</version>
  <packaging>jar</packaging>
</project>
'''

missing = []
for artifact, version, src in ITEMS:
    src_path = os.path.join('libs', src)
    if not os.path.exists(src_path):
        missing.append(src)
        continue
    dst_dir = os.path.join(GROUP_DIR, artifact, version)
    os.makedirs(dst_dir, exist_ok=True)
    jar_dst = os.path.join(dst_dir, '%s-%s.jar' % (artifact, version))
    shutil.copyfile(src_path, jar_dst)
    with open(os.path.join(dst_dir, '%s-%s.pom' % (artifact, version)), 'w', encoding='utf-8') as f:
        f.write(POM.format(artifact=artifact, version=version))
    print('%-22s %-10s %10d B' % (artifact, version, os.path.getsize(jar_dst)))

print()
print('missing:', missing if missing else 'none')
