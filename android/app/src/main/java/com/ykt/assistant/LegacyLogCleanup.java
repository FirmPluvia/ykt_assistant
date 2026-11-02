package com.ykt.assistant;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Upgrade-only cleanup of the retired app-owned log directory; symbolic links are never followed. */
final class LegacyLogCleanup {
    private LegacyLogCleanup() {}
    static void remove(File directory) throws IOException {
        if(!Files.exists(directory.toPath(),LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(directory.toPath(),new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir,IOException error) throws IOException { if(error!=null) throw error; Files.delete(dir); return FileVisitResult.CONTINUE; }
        });
    }
}
