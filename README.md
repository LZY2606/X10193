# 符号裁决庭 · Symbol Adjudication Court

在真正调用链接器之前，解释**“这个未定义符号会选中谁”**的本地符号裁决工具。
纯 Java 实现 ELF/ar 解析与链接裁决，**不调用系统 linker**；Spring Boot + SQLite 持久化方案。

## 运行

```bash
# 构建（跳过测试）
./gradlew build -x test

# 验收
./gradlew test
./gradlew bootRun --args='--server.address=127.0.0.1 --server.port=5253'
# 打开 http://127.0.0.1:5253 ，页面标题为「符号裁决庭」
```

## 能力

- **解析**（`court.elf`，ELF32/64、小端/大端，全部长度/索引边界校验）
  - section header、symtab/dynsym、strtab、Rel/Rela、SHT_GROUP(COMDAT)
  - `.gnu.version` / `.gnu.version_d` / `.gnu.version_r`：版本索引还原为版本名与别名链
  - `.dynamic`（DT_NEEDED/DT_SONAME）；无节区头时走 PT_DYNAMIC + DT_HASH/GNU_HASH 回退
  - GNU/BSD `ar` archive（长名表、符号索引、偶数填充）；thin archive 显式拒绝
- **裁决**（`court.engine.LinkEngine`）
  - strong / weak / common / local / protected / hidden / internal
  - 默认与非默认版本、版本别名（同一 Verdef 节点）、未满足版本
  - COMDAT 去重独立于普通覆盖规则
  - archive 成员惰性抽取 + 定点多轮扫描（处理归档循环依赖）；弱未定义不触发抽取
  - copy relocation 显式建模（`R_*_COPY`），不被普通覆盖吞掉
  - 两种目标平台策略：`STATIC_LD`（gABI 状态表）与 `RTLD_GLOBAL`（BFS 全局作用域）
  - 多个同等强定义时**保留歧义**（AMBIGUOUS），平局只允许使用显式加载顺序
  - 损坏对象只隔离自身（进入 badObjects），不影响同批其它对象
- **每个引用**输出：候选提供者列表、每个候选淘汰原因、层级排序、最终选择、受影响 relocation
- **方案版本化**（SQLite JSON 快照）：顺序或策略改变生成新版本；旧方案结果永不漂移
- **语义比较**：区分「换了提供者」(PROVIDER_CHANGED) 与「仅地址变化」(ADDRESS_ONLY)
- **页面**：对象依赖/COMDAT、符号候选与裁决、版本图、relocation 列表；archive 抽取与符号表更新可逐步播放

## 测试

测试用**程序在字节层面构造的小型 ELF/ar fixture**（`src/test/java/court/fixture`），
不依赖系统汇编器/链接器。覆盖 weak/strong、版本别名、hidden、common、COMDAT、
archive 循环依赖、未满足版本、重复候选歧义、copy relocation、损坏隔离与边界解析。

```bash
./gradlew test
```
