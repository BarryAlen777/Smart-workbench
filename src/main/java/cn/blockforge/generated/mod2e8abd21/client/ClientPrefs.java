package cn.blockforge.generated.mod2e8abd21.client;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端小偏好：记住上次在「可合成」列表里选中的分类和排序方式。
 * <p>
 * 就存两个词，走不了 Forge 配置那套（那是给整合包作者调参数用的），
 * 直接往 {@code config/smart_workbench-client.txt} 写两行枚举名（第一行分类、第二行排序），
 * 读失败/文件没有就回「全部」「默认」。旧版只有一行的文件也照常认（第二行缺当默认）。
 * 重启游戏也记得住。
 */
public final class ClientPrefs {

    private static CraftCategory cached;
    private static CraftSortMode cachedSort;

    private ClientPrefs() {
    }

    public static CraftCategory lastCategory() {
        if (cached == null) {
            load();
        }
        return cached;
    }

    public static CraftSortMode lastSortMode() {
        if (cachedSort == null) {
            load();
        }
        return cachedSort;
    }

    /** 点分类键时调：立刻落盘，退出游戏也不丢。 */
    public static void saveCategory(CraftCategory category) {
        if (cached == category) {
            return;
        }
        cached = category;
        flush();
    }

    /** 点排序键时调：同样立刻落盘。 */
    public static void saveSortMode(CraftSortMode mode) {
        if (cachedSort == mode) {
            return;
        }
        cachedSort = mode;
        flush();
    }

    private static void load() {
        cached = CraftCategory.ALL;
        cachedSort = CraftSortMode.DEFAULT;
        try {
            List<String> lines = Files.readAllLines(file(), StandardCharsets.UTF_8);
            if (!lines.isEmpty()) {
                String first = lines.get(0).trim();
                for (CraftCategory category : CraftCategory.values()) {
                    if (category.name().equalsIgnoreCase(first)) {
                        cached = category;
                        break;
                    }
                }
            }
            if (lines.size() > 1) {
                cachedSort = CraftSortMode.parse(lines.get(1).trim());
            }
        } catch (IOException | RuntimeException ignored) {
            // 第一次玩、文件还没有，或者被手改坏了：一律回默认值
            cached = CraftCategory.ALL;
            cachedSort = CraftSortMode.DEFAULT;
        }
    }

    private static void flush() {
        if (cached == null) {
            load();
        }
        try {
            List<String> lines = new ArrayList<>(2);
            lines.add(cached.name());
            lines.add(cachedSort == null ? CraftSortMode.DEFAULT.name() : cachedSort.name());
            ConfigFiles.writeAtomic(file(), lines);
        } catch (IOException | RuntimeException ignored) {
            // 写不进去就算了，最多下次打开回到记忆前的值，不影响玩
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("smart_workbench-client.txt");
    }
}
