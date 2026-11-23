package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.EditorLaunchRequest;
import com.ravi.controlcenter.model.EditorLaunchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service responsible for resolving source code file paths relative to the repository root,
 * enforcing directory traversal security, detecting host editor availability (VS Code with Notepad fallback),
 * and launching files asynchronously without blocking web threads.
 */
@Service
public class LocalEditorLauncherService {

    private static final Logger log = LoggerFactory.getLogger(LocalEditorLauncherService.class);

    private final Path repoRoot;

    public LocalEditorLauncherService(@Value("${platform.repo-root:}") String configuredRepoRoot) {
        this.repoRoot = resolveRepoRoot(configuredRepoRoot);
        log.info("LocalEditorLauncherService initialized with repository root: {}", this.repoRoot);
    }

    /**
     * Resolves and validates the target file path, detecting directory traversal or missing files.
     *
     * @param relativePath File path relative to repo root
     * @return Validated, normalized Path
     * @throws SecurityException if directory traversal is attempted
     * @throws FileNotFoundException if the target file does not exist
     */
    public Path resolveSecureFilePath(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }

        // Reject null bytes and illegal control characters
        if (relativePath.contains("\0")) {
            throw new SecurityException("Illegal null byte in file path");
        }

        // Normalize separators
        String cleanPath = relativePath.replace('\\', '/').trim();
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }

        Path resolved = repoRoot.resolve(cleanPath).normalize();

        // Enforce repository boundary (Directory Traversal Prevention)
        if (!resolved.startsWith(repoRoot)) {
            log.warn("Directory traversal attempt blocked: {}", relativePath);
            throw new SecurityException("Directory traversal forbidden: path outside repository root");
        }

        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            log.warn("File not found in repository: {} (resolved: {})", relativePath, resolved);
            throw new FileNotFoundException("File not found: " + relativePath);
        }

        // Real path symlink boundary check
        Path realPath = resolved.toRealPath();
        Path realRepo = repoRoot.toRealPath();
        if (!realPath.startsWith(realRepo)) {
            log.warn("Symlink traversal outside repository root blocked: {}", relativePath);
            throw new SecurityException("Symlink points outside repository root");
        }

        return realPath;
    }

    /**
     * Launches the target file in VS Code or Notepad asynchronously.
     *
     * @param request The editor launch request containing relative file path and line number
     * @return EditorLaunchResponse with status, editor name, and client launch URI
     */
    public EditorLaunchResponse launchEditor(EditorLaunchRequest request) {
        int line = request.resolvedLine();
        Path targetFile;
        try {
            targetFile = resolveSecureFilePath(request.filePath());
        } catch (SecurityException se) {
            log.error("Security violation for path: {}", request.filePath(), se);
            throw se;
        } catch (FileNotFoundException fe) {
            log.error("File not found: {}", request.filePath(), fe);
            throw new NoSuchElementException(fe.getMessage());
        } catch (IOException ioe) {
            log.error("IO error resolving path: {}", request.filePath(), ioe);
            throw new RuntimeException("Could not resolve file path: " + ioe.getMessage(), ioe);
        }

        String absPath = targetFile.toString();
        // Browser URL scheme: vscode://file/<path>:<line> (forward slashes)
        String forwardSlashPath = absPath.replace('\\', '/');
        String clientLaunchUrl = "vscode://file/" + forwardSlashPath + ":" + line;

        String vsCodeCmd = findVsCodeExecutable();
        if (vsCodeCmd != null) {
            boolean launched = executeProcessNonBlocking(buildVsCodeCommand(vsCodeCmd, absPath, line));
            if (launched) {
                return EditorLaunchResponse.success(
                    "VS_CODE",
                    request.filePath(),
                    absPath,
                    clientLaunchUrl,
                    "Opened in VS Code at line " + line
                );
            }
            log.warn("Failed to launch VS Code executable, falling back to Notepad for {}", absPath);
        }

        // Fallback to Notepad
        String notepadCmd = findNotepadExecutable();
        if (notepadCmd != null) {
            boolean launched = executeProcessNonBlocking(List.of(notepadCmd, absPath));
            if (launched) {
                return EditorLaunchResponse.success(
                    "NOTEPAD",
                    request.filePath(),
                    absPath,
                    clientLaunchUrl,
                    "VS Code not found; opened in Windows Notepad"
                );
            }
        }

        return EditorLaunchResponse.error(
            "NONE",
            request.filePath(),
            absPath,
            clientLaunchUrl,
            "Could not launch local editor process on host"
        );
    }

    /**
     * Non-blocking process execution discarding stdio to prevent hanging HTTP request threads.
     */
    private boolean executeProcessNonBlocking(List<String> command) {
        try {
            log.info("Executing process non-blocking: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectOutput(Redirect.DISCARD);
            pb.redirectError(Redirect.DISCARD);
            pb.start();
            return true;
        } catch (Exception e) {
            log.error("Failed to execute process: {}", command, e);
            return false;
        }
    }

    private List<String> buildVsCodeCommand(String executable, String absolutePath, int line) {
        String targetWithLine = absolutePath + ":" + line;
        if (executable.toLowerCase().endsWith(".cmd") || executable.toLowerCase().endsWith(".bat")) {
            return List.of("cmd.exe", "/c", executable, "--goto", targetWithLine);
        }
        return List.of(executable, "-g", targetWithLine);
    }

    /**
     * Detects VS Code executable on the host machine.
     */
    public String findVsCodeExecutable() {
        // 1. Direct standard Windows installations
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            Path directExe = Paths.get(localAppData, "Programs", "Microsoft VS Code", "Code.exe");
            if (Files.isRegularFile(directExe)) {
                return directExe.toString();
            }
            Path directCmd = Paths.get(localAppData, "Programs", "Microsoft VS Code", "bin", "code.cmd");
            if (Files.isRegularFile(directCmd)) {
                return directCmd.toString();
            }
        }

        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) {
            Path directExe = Paths.get(programFiles, "Microsoft VS Code", "Code.exe");
            if (Files.isRegularFile(directExe)) {
                return directExe.toString();
            }
            Path directCmd = Paths.get(programFiles, "Microsoft VS Code", "bin", "code.cmd");
            if (Files.isRegularFile(directCmd)) {
                return directCmd.toString();
            }
        }

        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (programFilesX86 != null) {
            Path directExe = Paths.get(programFilesX86, "Microsoft VS Code", "Code.exe");
            if (Files.isRegularFile(directExe)) {
                return directExe.toString();
            }
        }

        // 2. Check PATH environment variable
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(File.pathSeparator);
            for (String dir : dirs) {
                for (String candidate : List.of("Code.exe", "code.cmd", "code.exe", "code")) {
                    Path p = Paths.get(dir, candidate);
                    if (Files.isRegularFile(p)) {
                        return p.toString();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Detects Windows Notepad executable.
     */
    public String findNotepadExecutable() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) {
            Path notepad = Paths.get(systemRoot, "System32", "notepad.exe");
            if (Files.isRegularFile(notepad)) {
                return notepad.toString();
            }
        }

        // Search PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(File.pathSeparator);
            for (String dir : dirs) {
                Path p = Paths.get(dir, "notepad.exe");
                if (Files.isRegularFile(p)) {
                    return p.toString();
                }
            }
        }

        return "notepad.exe";
    }

    public Map<String, Object> getEditorStatus() {
        String vsCode = findVsCodeExecutable();
        String notepad = findNotepadExecutable();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("vsCodeAvailable", vsCode != null);
        status.put("vsCodePath", vsCode);
        status.put("notepadAvailable", notepad != null);
        status.put("notepadPath", notepad);
        status.put("detectedEditor", vsCode != null ? "VS_CODE" : (notepad != null ? "NOTEPAD" : "NONE"));
        status.put("repoRoot", repoRoot.toString());
        return status;
    }

    public Path getRepoRoot() {
        return repoRoot;
    }

    private static Path resolveRepoRoot(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path p = Paths.get(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p;
            }
        }

        // Auto-discover repository root by scanning parent directories for start.ps1 or .git
        Path current = Paths.get(".").toAbsolutePath().normalize();
        Path candidate = current;
        while (candidate != null) {
            if (Files.exists(candidate.resolve("start.ps1")) || Files.exists(candidate.resolve(".git"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }

        return current;
    }
}
