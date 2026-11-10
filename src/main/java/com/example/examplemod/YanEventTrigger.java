package com.example.examplemod;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 通用事件触发器（进度/成就用）。
 *
 * <p>只有一件事要记：某个「事件名」发生过。JSON 里写
 * {@code "trigger": "yanscriptures:event", "conditions": {"event": "trial_clear"}}，
 * 代码里 {@code YanEventTrigger.INSTANCE.trigger(player, "trial_clear")}。</p>
 *
 * <p>为什么不用原版触发器：试炼开始/结束、炼药锅成锅、著书台写出一本书，
 * 这三类事件在原版里都没有对应的 criterion（`inventory_changed` 只能证明玩家手里有过某物，
 * 证明不了「是这台机器做出来的」）。</p>
 */
public final class YanEventTrigger extends SimpleCriterionTrigger<YanEventTrigger.TriggerInstance> {

    /** 全局唯一实例：注册一次，所有调用点共用。 */
    public static final YanEventTrigger INSTANCE = new YanEventTrigger();

    private static final ResourceLocation ID = new ResourceLocation(ExampleMod.MODID, "event");

    private YanEventTrigger() {
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                             DeserializationContext context) {
        String event = json.has("event") ? json.get("event").getAsString() : "";
        return new TriggerInstance(player, event);
    }

    /** 触发一次；{@code event} 与 JSON 里写的名字对比。 */
    public void trigger(ServerPlayer player, String event) {
        super.trigger(player, instance -> instance.matches(event));
    }

    /** 一条 criterion：只关心 event 名字，不关心别的条件。 */
    public static final class TriggerInstance extends AbstractCriterionTriggerInstance {

        private final String event;

        public TriggerInstance(ContextAwarePredicate player, String event) {
            super(ID, player);
            this.event = event;
        }

        public boolean matches(String other) {
            return this.event.isEmpty() || this.event.equals(other);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.addProperty("event", event);
            return json;
        }
    }
}
