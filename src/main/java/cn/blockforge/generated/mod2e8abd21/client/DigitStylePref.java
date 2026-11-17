package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * 右下角 / 页签 / 折叠头那串数量数字用哪一款：<b>0.7 倍原版字体（九遍描边）</b>还是
 * <b>自绘 3×5 点阵</b>。
 * <p>
 * 玩家在界面里按「数量数字样式」键（键位在「选项 → 控制 → 按键绑定 → 智能工作台」里改，默认 F8）
 * 来回换，换完立刻生效、并写进 {@code config/smart_workbench-client.toml}——
 * 下次开游戏还是上次那一款，不用每回重新按。
 * <p>
 * 为什么单独放一份 <b>客户端</b>配置（Type.CLIENT）而不是挤进 common：
 * 这只是「我自己看着舒服」的画面偏好，跟服务器的玩法规则没关系。放进服务器配置的话，
 * 多人服里玩家改不动、还会被服务器的值盖回去；客户端配置只存在玩家自己电脑上，各人管各人的。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DigitStylePref {

    /** 客户端配置规格；文件名见 {@link #register()}。 */
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue PIXEL_DIGITS;

    /** 缓存值：{@code null} 表示还没从配置文件里读到过（这时按默认的 0.7 倍字体画）。 */
    private static volatile Boolean cached;

    /** 配置读进来之后留着这个实例，玩家在界面里切换时靠它把改动立刻落盘。 */
    private static volatile ModConfig loadedConfig;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("智能工作台 客户端外观设置（只影响你自己的画面，不进存档、不进服务器）");
        PIXEL_DIGITS = builder
                .comment("数量数字用哪一款：",
                        "false（默认）：原版字体缩到 0.7 倍、八方向九遍描边，笔画清楚、和原版风格一致；",
                        "true：自绘 3×5 点阵，一格像素就是一格像素、完全不缩放，最锐利，但字形比字体版粗一点。",
                        "游戏里按「数量数字样式」键（默认 F8）也能切，界面里切完会自动写回这个文件。")
                .define("pixelCountDigits", false);
        SPEC = builder.build();
    }

    private DigitStylePref() {
    }

    /**
     * 注册客户端配置。只在客户端注册：专用服务器上没有画面，注册了也读不到东西。
     * 由 {@link cn.blockforge.generated.mod2e8abd21.ModConfig#register()} 在构造阶段调用
     * （配置必须在模组加载阶段注册，晚到客户端初始化就来不及了）。
     */
    public static void register() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT, SPEC, "smart_workbench-client.toml");
    }

    /** 当前是不是用点阵那一款（默认 false = 0.7 倍字体）。 */
    public static boolean pixelDigits() {
        Boolean value = cached;
        if (value == null) {
            value = readSpec(false);
            cached = value;
        }
        return value;
    }

    /**
     * 换一款并立刻写回配置文件。
     * <p>
     * 配置还没读进来（极早期调用）时只改缓存，不抛异常——反正 {@link #capture} 读文件时
     * 会以文件里的值为准，那之前界面也还没画。
     */
    public static void setPixelDigits(boolean pixel) {
        cached = pixel;
        try {
            PIXEL_DIGITS.set(pixel);
            ModConfig config = loadedConfig;
            if (config != null) {
                config.save();
            }
        } catch (RuntimeException ignored) {
            // 配置文件只读或被占用时不让游戏崩：这一局照样按 cached 的画法显示。
        }
    }

    /** 配置加载 / 重载时把值读进缓存，顺便记住 ModConfig 实例好落盘。 */
    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) {
        capture(event.getConfig());
    }

    @SubscribeEvent
    public static void onReloading(ModConfigEvent.Reloading event) {
        capture(event.getConfig());
    }

    private static void capture(ModConfig config) {
        if (config.getSpec() != SPEC) {
            return;
        }
        loadedConfig = config;
        cached = readSpec(false);
    }

    /** 读配置值；万一此时规格还没加载好（{@code get()} 会抛），退回默认值，不让界面炸。 */
    private static boolean readSpec(boolean fallback) {
        try {
            return Boolean.TRUE.equals(PIXEL_DIGITS.get());
        } catch (RuntimeException notLoadedYet) {
            return fallback;
        }
    }
}
