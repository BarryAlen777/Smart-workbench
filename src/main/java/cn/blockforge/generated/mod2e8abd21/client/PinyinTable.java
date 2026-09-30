package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * 离线拼音表：把汉字映射成不带声调的 ASCII 拼音（ü 记作 v）。
 * <p>
 * 数据来自 Unihan 的 kMandarin 字段，由 tools/gen_pinyin_table.py 在开发时抽成
 * {@code assets/smart_workbench/pinyin.tsv.gz} 随模组打包，玩家端不需要联网，
 * 也不依赖 REI 之类的外部模组。
 * <p>
 * 表是懒加载的：第一次搜索时才读，读进来常驻内存（约两万个汉字，几百 KB）。
 */
public final class PinyinTable {

    private static final String RESOURCE = "/assets/smart_workbench/pinyin.tsv.gz";

    private static Map<Character, String> table;
    private static boolean loaded;

    private PinyinTable() {
    }

    /** 查一个汉字的拼音；不是汉字（或表里没有）返回 null。 */
    public static String pinyin(char c) {
        ensureLoaded();
        return table == null ? null : table.get(c);
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        table = load();
    }

    private static Map<Character, String> load() {
        // 主路径：直接从模组 jar 里读（开发环境就是 build/resources/main）。
        InputStream raw = PinyinTable.class.getResourceAsStream(RESOURCE);
        if (raw == null) {
            // 兜底：个别环境下模组类的 classloader 拿不到自己的资源，就走客户端的资源管理器。
            raw = openFromResourceManager();
        }
        if (raw == null) {
            return null;
        }
        try (InputStream stream = raw) {
            Map<Character, String> map = new HashMap<>(32768);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(stream), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0) {
                        continue;
                    }
                    int codePoint = Integer.parseInt(line.substring(0, tab), 16);
                    // 多音字按空格分隔，只取最常用的一读，避免搜索词命中冷门读音
                    String readings = line.substring(tab + 1).trim();
                    int space = readings.indexOf(' ');
                    String pinyin = space < 0 ? readings : readings.substring(0, space);
                    if (!pinyin.isEmpty()) {
                        map.put((char) codePoint, pinyin);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            // 拼音表读不出来就静默降级：只按物品名/ID 搜索，不让界面崩掉
            return null;
        }
    }

    private static InputStream openFromResourceManager() {
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(new ResourceLocation(SmartWorkbenchMod.MOD_ID, "pinyin.tsv.gz"));
            if (resource.isEmpty()) {
                return null;
            }
            return resource.get().open();
        } catch (Exception e) {
            return null;
        }
    }
}
