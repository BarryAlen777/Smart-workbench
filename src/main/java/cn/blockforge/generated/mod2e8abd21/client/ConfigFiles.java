package cn.blockforge.generated.mod2e8abd21.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 本地账本文件（收藏 / 队列 / 偏好）的安全落盘：先写同目录临时文件，再原子替换正式文件。
 * <p>
 * 直接 {@code Files.write} 覆盖是把旧文件截断重写——写到一半崩溃/断电，
 * 留下的就是半截文件，整份账作废。临时文件 + {@code ATOMIC_MOVE} 要么旧的在、
 * 要么新的全在，不存在中间态。文件系统不支持原子移动（极少数网络盘）就退回普通覆盖，
 * 至少不比原来差。
 */
final class ConfigFiles {

    private ConfigFiles() {
    }

    static void writeAtomic(Path file, List<String> lines) throws IOException {
        Path dir = file.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
