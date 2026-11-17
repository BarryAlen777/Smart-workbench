package cn.blockforge.generated.mod2e8abd21.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 判断"两个坐标是不是同一台双格方块的两半"（原版大箱子那一类）。
 *
 * <p>这里<b>刻意不引用</b> {@code ChestBlock} / {@code ChestType}（r243）：判断"是不是箱子、
 * 朝哪边、左半还是右半"全部走 {@link BlockState} 的通用属性接口，只认属性名 {@code type}
 * 和 {@code facing}。这样一来，本模组的类加载路径上完全不出现箱子类——装了会对箱子类做
 * mixin 的模组（比如 Legendary Block Entities）时，启动阶段就没有可撞的地方；而判定条件
 * 和原版 {@code ChestBlock.getConnectedDirection} 逐条等价（LEFT 取朝向顺时针、
 * RIGHT 取朝向逆时针），"整台大箱子一个框"的显示效果保持不变。
 *
 * <p>不写死方块名单，是因为很多模组箱子直接沿用原版 {@code type}/{@code facing} 这套属性，
 * 靠属性名判断能一起覆盖；而桶、熔炉这些没有 {@code type} 的方块自然判 false，不会被误并。
 */
public final class PairedBlockHalves {

    private static final String TYPE_PROPERTY = "type";
    private static final String FACING_PROPERTY = "facing";
    private static final String LEFT = "left";
    private static final String RIGHT = "right";

    private PairedBlockHalves() {
    }

    /**
     * 两个坐标是否互为同一台双格方块的两半：同一种方块、一边 LEFT 一边 RIGHT、
     * 并且按各自的朝向互相指认（A 按自己的朝向指到 B，B 按自己的朝向正好指回 A）。
     *
     * <p>只读方块状态，不改任何东西；坐标未加载时判 false，不触发区块加载。
     */
    public static boolean arePaired(Level level, BlockPos a, BlockPos b) {
        if (level == null || a == null || b == null) {
            return false;
        }
        if (!level.isLoaded(a) || !level.isLoaded(b)) {
            return false;
        }
        return arePairedStates(level.getBlockState(a), a, level.getBlockState(b), b);
    }

    /** {@link #arePaired} 的纯状态版本：不碰 {@code Level}，方便脱离世界单独验证。 */
    public static boolean arePairedStates(BlockState stateA, BlockPos a, BlockState stateB, BlockPos b) {
        if (stateA == null || stateB == null || a == null || b == null) {
            return false;
        }
        if (!stateA.is(stateB.getBlock())) {
            return false;
        }
        String typeA = halfOf(stateA);
        String typeB = halfOf(stateB);
        if (!isLeftOrRight(typeA) || !isLeftOrRight(typeB) || typeA.equals(typeB)) {
            return false;
        }
        Direction toB = connectDirection(stateA);
        if (toB == null || !b.equals(a.relative(toB))) {
            return false;
        }
        Direction back = connectDirection(stateB);
        return back != null && back.getOpposite() == toB;
    }

    /**
     * 照原版 {@code ChestBlock.getConnectedDirection} 的算法：LEFT 取朝向顺时针，
     * RIGHT 取朝向逆时针。缺属性或状态不对时返回 {@code null}。
     */
    private static Direction connectDirection(BlockState state) {
        Direction facing = facingOf(state);
        String half = halfOf(state);
        if (facing == null) {
            return null;
        }
        if (LEFT.equals(half)) {
            return facing.getClockWise();
        }
        if (RIGHT.equals(half)) {
            return facing.getCounterClockWise();
        }
        return null;
    }

    private static Direction facingOf(BlockState state) {
        return propertyValue(state, FACING_PROPERTY) instanceof Direction direction ? direction : null;
    }

    private static String halfOf(BlockState state) {
        return token(propertyValue(state, TYPE_PROPERTY));
    }

    /** 按属性名取值：方块没有这个属性就返回 {@code null}（桶、熔炉这类天然被挡掉）。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object propertyValue(BlockState state, String name) {
        for (Property property : state.getProperties()) {
            if (name.equals(property.getName())) {
                return state.getValue(property);
            }
        }
        return null;
    }

    /** 枚举属性统一取序列化名（ChestType 是 left/right/single，Direction 是 north/south/...）。 */
    private static String token(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof StringRepresentable representable) {
            return representable.getSerializedName();
        }
        return value.toString();
    }

    private static boolean isLeftOrRight(String half) {
        return LEFT.equals(half) || RIGHT.equals(half);
    }
}
