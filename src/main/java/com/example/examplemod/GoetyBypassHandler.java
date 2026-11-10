// [已迁移 2026-10-23]
//
// 这个类原本叫 GoetyBypassHandler —— 名字里直接写了别的模组，且内部写死了
// goety:apostle、moddedInvul、HIT_COOLDOWN、MOVEMENT_SLOWDOWN、goety:doom
// 以及两个模组的配置类名。虽然当时是为了让功能尽快跑通，但这属于"指名道姓"，
// 别的作者看到自己的东西被写死在别人的模组里会反感。
//
// 现在全部规则都搬到了 {@link com.example.examplemod.BookOfMeCombatHandler}：
//   R1 负面效果免疫   → MobEffect#isBeneficial()（原版语义）
//   R2 无敌帧穿透     → 按字段名语义扫描 invul / iframe / cooldown / hurtcool
//   R3 生物伤害免疫   → Monster 基类（原版语义）
//   R4 限伤穿透       → 临时抬高名字含 cap / limit 的数值配置（唯一保留的类名，见该类注释）
//
// 本文件没有类定义，只为保留迁移说明，可以安全删除。
