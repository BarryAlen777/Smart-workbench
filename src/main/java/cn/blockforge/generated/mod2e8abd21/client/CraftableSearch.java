package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.menu.CraftableEntry;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 可合成列表的搜索：一个物品要能被三种写法搜到 ——
 * <ol>
 *   <li>物品名或注册 ID 里的原文（中文、英文、下划线 ID 都算，比如「木板」「planks」「oak_planks」）；</li>
 *   <li>整串拼音（输入 {@code muban} 命中「木板」）；</li>
 *   <li>拼音首字母（输入 {@code mb} 也命中「木板」）。</li>
 * </ol>
 * 拼音表见 {@link PinyinTable}；每个物品名的三种写法只算一次，之后走缓存。
 */
public final class CraftableSearch {

    /** 缓存上限：一个整合包的物品种类也就几千，超了就整批清空重来，逻辑简单也不会卡。 */
    private static final int CACHE_LIMIT = 8192;

    private static final Map<String, Keys> CACHE = new HashMap<>();

    private CraftableSearch() {
    }

    /** 这个物品是不是命中搜索词；搜索词为空表示全都命中。 */
    public static boolean matches(CraftableEntry entry, String query) {
        String needle = normalize(query);
        if (needle.isEmpty()) {
            return true;
        }
        Keys keys = keysFor(entry.result());
        return keys.flat.contains(needle)
                || keys.pinyin.contains(needle)
                || keys.initials.contains(needle);
    }

    /** 物品名的三种可搜索写法。 */
    private record Keys(String flat, String pinyin, String initials) {
    }

    private static Keys keysFor(ItemStack stack) {
        String name = stack.getHoverName().getString();
        String descriptionId = stack.getItem().getDescriptionId();
        String cacheKey = name + '\u0000' + descriptionId;
        synchronized (CACHE) {
            Keys cached = CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            Keys built = build(name, descriptionId);
            if (CACHE.size() >= CACHE_LIMIT) {
                CACHE.clear();
            }
            CACHE.put(cacheKey, built);
            return built;
        }
    }

    private static Keys build(String name, String descriptionId) {
        // 原文：物品名 + 注册 ID（fabric 的 item.xxx、原版的 block.xxx 都在里面），
        // 统一压成小写、只留字母数字和汉字，这样「橡木 楼梯」「oak_stairs」都能命中。
        StringBuilder flat = new StringBuilder(name.length() + descriptionId.length() + 8);
        appendSearchable(flat, name);
        appendSearchable(flat, descriptionId);

        // 拼音：汉字取拼音，英文/数字原样保留（比如「TNT」还是 tnt）。
        StringBuilder pinyin = new StringBuilder(name.length() * 4);
        StringBuilder initials = new StringBuilder(name.length());
        name.codePoints().forEach(codePoint -> {
            String py = PinyinTable.pinyin((char) codePoint);
            if (py != null) {
                pinyin.append(py);
                initials.append(py.charAt(0));
            } else if (isAsciiAlphaNumeric(codePoint)) {
                pinyin.appendCodePoint(Character.toLowerCase(codePoint));
                initials.appendCodePoint(Character.toLowerCase(codePoint));
            }
        });
        return new Keys(flat.toString(), pinyin.toString(), initials.toString());
    }

    private static void appendSearchable(StringBuilder out, String text) {
        text.codePoints().forEach(codePoint -> {
            if (isAsciiAlphaNumeric(codePoint)) {
                out.appendCodePoint(Character.toLowerCase(codePoint));
            } else if (isCjk(codePoint)) {
                out.appendCodePoint(codePoint);
            }
        });
    }

    /** 搜索词也做同样的归一化，玩家多打了空格、大小写不一样都没关系。 */
    private static String normalize(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(query.length());
        appendSearchable(out, query);
        return out.toString();
    }

    private static boolean isAsciiAlphaNumeric(int codePoint) {
        return (codePoint >= '0' && codePoint <= '9')
                || (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= 'A' && codePoint <= 'Z');
    }

    private static boolean isCjk(int codePoint) {
        return codePoint >= 0x4E00 && codePoint <= 0x9FFF;
    }
}
