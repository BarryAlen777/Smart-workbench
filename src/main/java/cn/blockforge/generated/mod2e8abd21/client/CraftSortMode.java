package cn.blockforge.generated.mod2e8abd21.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * 右侧「可合成」列表的排序方式（界面上的「排序」按钮循环切换，选择记进 {@link ClientPrefs}）。
 * <ul>
 *   <li>{@link #DEFAULT}：服务端给的顺序——材料齐的直接项在前（按注册名），链式的垫底。什么都不想的时候就用它；</li>
 *   <li>{@link #NAME}：按产物显示名排（中文按拼音），列表长了找东西最快；</li>
 *   <li>{@link #RECENT}：最近做过的在前（配合「常用收藏」的使用记录），连续几天做同一批东西时省得翻。</li>
 * </ul>
 * 三种排法都<b>不影响</b>收藏项钉顶：钉住的那几件永远在最前面，这一条和用户约定过。
 */
public enum CraftSortMode {

    DEFAULT, NAME, RECENT;

    /** 点一下按钮换下一档：默认 → 名称 → 常用 → 默认。 */
    public CraftSortMode next() {
        return switch (this) {
            case DEFAULT -> NAME;
            case NAME -> RECENT;
            case RECENT -> DEFAULT;
        };
    }

    /** 按钮上的两个字。 */
    public MutableComponent label() {
        return Component.translatableWithFallback(langKey(), fallbackName());
    }

    /** 这一档的翻译键（界面侧做静态文字缓存时当表键用）。 */
    public String langKey() {
        return "gui.smart_workbench.sort." + name().toLowerCase(Locale.ROOT);
    }

    /** 配置文件里存/读的就是枚举名；坏了回默认。 */
    public static CraftSortMode parse(String raw) {
        for (CraftSortMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        return DEFAULT;
    }

    /** 词条缺失时的兜底名（也是缓存表里的回退文案）。 */
    public String fallbackName() {
        return switch (this) {
            case DEFAULT -> "默认";
            case NAME -> "名称";
            case RECENT -> "常用";
        };
    }
}
