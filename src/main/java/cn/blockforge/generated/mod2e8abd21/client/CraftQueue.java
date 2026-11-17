package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.menu.QueuedCraft;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import cn.blockforge.generated.mod2e8abd21.network.C2SQueuePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 合成队列的客户端账本：排了哪些项（配方 + 目标个数 + 已做出多少）、顺序、增删改，都在这里；
 * 同时负责把它存到 {@code config/smart_workbench-queue.txt}——按玩家的口味<b>跨会话记住</b>，
 * 和常用收藏一个待遇（放全局 config，不放存档目录）。
 * <p>
 * 分工：客户端是唯一账本，服务端只是执行器。每次改动都整份 SYNC 给服务端
 * （打开界面时也补发一次）；服务端每有进展回推状态，这里收进 {@link #applyServerDone}
 * 顺手落盘——所以中途关界面、重开游戏，做到一半的进度都不会“被忘掉再做一遍”。
 * 队列项的图标物品不走网络：存档里记物品 ID，读回来摆个样，真动手合成永远由服务端
 * 按配方 ID 重新解析，配方没了会明确报「这份配方已经不存在了」。
 */
public final class CraftQueue {

    /** 队列里的一项。target/done 都按产物个数算。 */
    public static final class Entry {
        public final int id;
        public final ResourceLocation recipeId;
        public final ItemStack display;
        public int target;
        public int done;

        Entry(int id, ResourceLocation recipeId, ItemStack display, int target, int done) {
            this.id = id;
            this.recipeId = recipeId;
            this.display = display;
            this.target = target;
            this.done = done;
        }
    }

    private static final int MAX_ENTRIES = SmartWorkbenchMenu.QUEUE_MAX_ENTRIES;
    private static final int MAX_TARGET = SmartWorkbenchMenu.QUEUE_MAX_TARGET;

    private static List<Entry> entries;
    private static int nextId = 1;

    /**
     * 落盘节流：队列执行中服务端每 tick（50ms）回推一次进度，每件产物都要对一次账——
     * 照原样就是每秒 20 次全文件重写。改成「置脏，最多一秒真写一次」，
     * 关界面（{@link #flushSave()}）时强制补一次；就算下一秒前游戏崩了，最多重做一件，
     * 不会出现整份队列账被写坏或刷屏卡顿。
     */
    private static final long SAVE_THROTTLE_MS = 1000L;
    private static boolean saveDirty;
    private static long lastSaveMs;

    private CraftQueue() {
    }

    /** 当前队列（不可当写入口用，改动一律走下面的方法）。 */
    public static List<Entry> entries() {
        ensureLoaded();
        return entries;
    }

    public static boolean isEmpty() {
        ensureLoaded();
        return entries.isEmpty();
    }

    public static int size() {
        ensureLoaded();
        return entries.size();
    }

    /** 还有没有没做完的项（「开始」按钮该不该是新开一轮）。 */
    public static boolean hasPending() {
        ensureLoaded();
        for (Entry e : entries) {
            if (e.done < e.target) {
                return true;
            }
        }
        return false;
    }

    public static Entry findById(int id) {
        ensureLoaded();
        for (Entry e : entries) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    /**
     * 同配方已在队里就加量，否则追加一项。
     *
     * @return 真正排进去的个数（可能被单项目标上限裁短）；队列满了返回 {@code -1}（界面负责出提示）
     */
    public static int add(ResourceLocation recipeId, ItemStack result, int amount) {
        ensureLoaded();
        int qty = Math.max(1, Math.min(amount, MAX_TARGET));
        for (Entry e : entries) {
            if (e.recipeId.equals(recipeId)) {
                int before = e.target;
                e.target = Math.min(MAX_TARGET, e.target + qty);
                commit();
                return e.target - before;
            }
        }
        if (entries.size() >= MAX_ENTRIES) {
            return -1;
        }
        entries.add(new Entry(nextId++, recipeId, result.copyWithCount(1), qty, 0));
        commit();
        return qty;
    }

    public static void remove(int id) {
        ensureLoaded();
        entries.removeIf(e -> e.id == id);
        commit();
    }

    /** 上下移动一项；到边就原样不动。 */
    public static void move(int id, int delta) {
        ensureLoaded();
        int at = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id == id) {
                at = i;
                break;
            }
        }
        int to = at + delta;
        if (at < 0 || to < 0 || to >= entries.size()) {
            return;
        }
        Entry e = entries.remove(at);
        entries.add(to, e);
        commit();
    }

    /** 滚轮调目标数量，夹在 [1, {@value #MAX_TARGET}]（删除只走 × 按钮，防滚过头误删）。 */
    public static void adjust(int id, int delta) {
        ensureLoaded();
        Entry e = findById(id);
        if (e == null) {
            return;
        }
        int next = e.target + delta;
        if (next < 1) {
            next = 1;
        }
        if (next > MAX_TARGET) {
            next = MAX_TARGET;
        }
        if (next == e.target) {
            return;
        }
        e.target = next;
        commit();
    }

    /**
     * 直接把目标数量设成某个值（面板里双击数量、打字填的入口）。
     * 夹在 [1, {@value #MAX_TARGET}]；跟上一次的数值一样就什么都不做，免得白发一份 SYNC。
     */
    public static void setTarget(int id, int value) {
        ensureLoaded();
        Entry e = findById(id);
        if (e == null) {
            return;
        }
        int next = Math.max(1, Math.min(MAX_TARGET, value));
        if (next == e.target) {
            return;
        }
        e.target = next;
        commit();
    }

    /** 单项目标数量上限（面板里的输入框要按它限制位数/提示）。 */
    public static int maxTarget() {
        return MAX_TARGET;
    }

    public static void clear() {
        ensureLoaded();
        entries.clear();
        commit();
    }

    /**
     * 全做完后点「再来一轮」：客户端先把每项进度清零再推一份 SYNC，然后发 START。
     * 服务端收 SYNC 对同 id 取「两边较大值」（防卡顿时的旧账本把已完成打回去），
     * 但那份保护分不清"旧账本"和"新一轮"——新轮已经起步、客户端却还揣着上一轮的满 done 时，
     * 任何一次改动的 SYNC 都会把旧 done 顶回服务端、让这一轮白跳过。重跑前主动清账就没这问题。
     */
    public static void resetDoneAndSync(SmartWorkbenchMenu menu) {
        ensureLoaded();
        for (Entry e : entries) {
            e.done = 0;
        }
        markDirty();
        flushSave();
        syncToServer(menu);
    }

    /** 服务端状态回包：按项 id 对账进度。找不到对不上号的项就安静忽略（玩家刚删了它）。 */
    public static void applyServerDone(Map<Integer, Integer> done) {
        ensureLoaded();
        boolean changed = false;
        for (Entry e : entries) {
            Integer value = done.get(e.id);
            if (value != null && e.done != value) {
                e.done = value;
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    /** 把整份队列推给服务端（界面打开时补一次，此后每次改动自动跟一次）。空队列也要发：清空了不告诉服务端它会拿着旧的白跑。 */
    public static void syncToServer(SmartWorkbenchMenu menu) {
        ensureLoaded();
        if (menu == null) {
            return;
        }
        List<QueuedCraft> wire = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            wire.add(new QueuedCraft(e.id, e.recipeId, e.target, e.done));
        }
        ModNetwork.sendToServer(new C2SQueuePacket(menu.getBenchPos(), SmartWorkbenchMenu.QUEUE_OP_SYNC, wire, -1));
    }

    /** 发一条执行指令（开始 / 暂停 / 跳过）。 */
    public static void sendOp(SmartWorkbenchMenu menu, int op, int entryId) {
        if (menu == null) {
            return;
        }
        ModNetwork.sendToServer(new C2SQueuePacket(menu.getBenchPos(), op, List.of(), entryId));
    }

    /** 玩家改了账本（加删项、调数量、换序）：立刻落盘，顺带把整份队列推给正开着的界面背后的
     * 服务端（漏一步服务端就会拿着旧队列白跑）。这类操作频率低，不用节流。 */
    private static void commit() {
        markDirty();
        flushSave();
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof SmartWorkbenchMenu menu) {
            syncToServer(menu);
        }
    }

    /* —— 存档：一行一项「id|配方ID|物品ID|物品数|目标|已做」 —— */

    private static void ensureLoaded() {
        if (entries == null) {
            entries = read();
        }
    }

    private static List<Entry> read() {
        List<Entry> list = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                String raw = line.trim();
                if (raw.isEmpty()) {
                    continue;
                }
                String[] parts = raw.split("\\|");
                if (parts.length < 5) {
                    continue;
                }
                ResourceLocation recipeId = ResourceLocation.tryParse(parts[1]);
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(parts[2]));
                if (recipeId == null || item == null) {
                    // 配方或物品已经不存在了：留着也没法做，直接丢
                    continue;
                }
                try {
                    int id = Integer.parseInt(parts[0]);
                    int count = Math.max(1, Integer.parseInt(parts[3]));
                    int target = Math.max(1, Math.min(MAX_TARGET, Integer.parseInt(parts[4])));
                    int done = parts.length > 5 ? Math.max(0, Integer.parseInt(parts[5])) : 0;
                    list.add(new Entry(id, recipeId, new ItemStack(item, Math.min(count, item.getMaxStackSize())),
                            target, Math.min(done, target)));
                    nextId = Math.max(nextId, id + 1);
                } catch (NumberFormatException ignored) {
                    // 一行坏了丢一行
                }
                if (list.size() >= MAX_ENTRIES) {
                    break;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 第一次玩、文件没有或被手改坏：空队列起步
        }
        // 物品被注册表回收后 get 返回 AIR，这种项展示不出名堂，静默剔除
        for (Iterator<Entry> it = list.iterator(); it.hasNext(); ) {
            if (it.next().display.isEmpty()) {
                it.remove();
            }
        }
        return list;
    }

    /** 记了账：置脏，够久了才真写一次（节流），别队列每做一件就重写整个文件。 */
    private static void markDirty() {
        saveDirty = true;
        long now = System.currentTimeMillis();
        if (now - lastSaveMs >= SAVE_THROTTLE_MS) {
            flushSave();
        }
    }

    /** 渲染循环每帧叫一次：脏账且距上次落盘够久了才真写（节流的时间推进点）。 */
    public static void flushIfDue() {
        if (saveDirty && System.currentTimeMillis() - lastSaveMs >= SAVE_THROTTLE_MS) {
            flushSave();
        }
    }

    /** 强制把脏账落盘：关界面时调一次。 */
    public static void flushSave() {
        if (!saveDirty) {
            return;
        }
        saveDirty = false;
        lastSaveMs = System.currentTimeMillis();
        try {
            List<String> lines = new ArrayList<>(entries.size());
            for (Entry e : entries) {
                lines.add(e.id + "|" + e.recipeId + "|" + BuiltInRegistries.ITEM.getKey(e.display.getItem())
                        + "|" + e.display.getCount() + "|" + e.target + "|" + e.done);
            }
            ConfigFiles.writeAtomic(file(), lines);
        } catch (IOException | RuntimeException ignored) {
            // 写不进去就算了，最多下次打开队伍没了，不影响玩
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("smart_workbench-queue.txt");
    }
}
