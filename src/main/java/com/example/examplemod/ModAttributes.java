package com.example.examplemod;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ExampleMod.MODID);

    // 定义一个"魔法伤害"属性
    public static final RegistryObject<Attribute> MAGIC_DAMAGE = ATTRIBUTES.register("magic_damage",
            () -> new RangedAttribute("attribute.yanscriptures.magic_damage", 0.0, 0.0, 1024.0).setSyncable(true));
}