package tech.kzen.shell.util

import org.slf4j.LoggerFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


// Atomic move with a cross-store fallback: ATOMIC_MOVE fails when source and target sit on
//  different stores, where a plain move (copy then delete) is the best available. Intentionally
//  duplicated in kzen-launcher's AtomicMoveUtil (the launcher depends on neither kzen-lib nor
//  kzen-shell — same rationale as SecurityGate) — keep the copies in sync.
object AtomicMoveUtil {
    private val logger = LoggerFactory.getLogger(AtomicMoveUtil::class.java)!!


    fun move(source: Path, target: Path, replaceExisting: Boolean = false) {
        try {
            if (replaceExisting) {
                Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
            }
        }
        catch (e: AtomicMoveNotSupportedException) {
            logger.info("atomic move unsupported ({}), copying across stores: {} -> {}",
                e.message, source, target)

            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
            else {
                Files.move(source, target)
            }
        }
    }
}
