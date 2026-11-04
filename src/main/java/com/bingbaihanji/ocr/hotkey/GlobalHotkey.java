package com.bingbaihanji.ocr.hotkey;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

// Win32 全局热键（JDK FFM）：RegisterHotKey + 专用消息循环线程。
// hWnd 传 NULL → WM_HOTKEY 投递到注册线程的消息队列，故注册/注销都在循环线程执行。
public final class GlobalHotkey implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GlobalHotkey.class);

    private static final int WM_QUIT = 0x0012;
    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_APP_WAKE = 0x8001; // 自定义唤醒消息

    // Win32 MSG 结构（x64）：hwnd@0(8) message@8(4) pad wParam@16(8) lParam@24(8) time@32(4) pt@36(8) lPrivate@44(4)
    private static final long MSG_MESSAGE_OFFSET = 8;
    private static final long MSG_WPARAM_OFFSET = 16;
    private static final long MSG_SIZE = 48;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
    private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

    private static final MethodHandle REGISTER_HOT_KEY = downcall("RegisterHotKey",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle UNREGISTER_HOT_KEY = downcall("UnregisterHotKey",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle GET_MESSAGE_W = downcall("GetMessageW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle POST_THREAD_MESSAGE_W = downcall("PostThreadMessageW",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle GET_CURRENT_THREAD_ID = downcall("GetCurrentThreadId",
            FunctionDescriptor.of(JAVA_INT));

    private final Consumer<Integer> onHotkey;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final Map<Integer, HotkeyBinding> registered = new ConcurrentHashMap<>();
    private final CountDownLatch ready = new CountDownLatch(1);
    private volatile boolean running = true;
    private volatile int threadId;
    private final Thread thread;

    // onHotkey 收到热键 id（已投递到 JavaFX 应用线程）
    public GlobalHotkey(Consumer<Integer> onHotkey) {
        this.onHotkey = onHotkey;
        this.thread = new Thread(this::loop, "global-hotkey");
        this.thread.setDaemon(true);
        this.thread.start();
        try {
            if (!ready.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("全局热键线程启动超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("全局热键线程启动被中断", e);
        }
    }

    // 注册热键；冲突/失败返回 false
    public boolean register(int id, HotkeyBinding binding) {
        unregister(id);
        int mods = binding.modifiers() | HotkeyBinding.MOD_NOREPEAT;
        int vk = binding.virtualKey();
        boolean ok = submitAndWait(() -> invokeInt(REGISTER_HOT_KEY, MemorySegment.NULL, id, mods, vk) != 0);
        if (ok) {
            registered.put(id, binding);
            log.info("全局热键已注册 id={} {}", id, binding.display());
        } else {
            log.warn("全局热键注册失败（可能被占用）id={} {}", id, binding.display());
        }
        return ok;
    }

    // 注销热键（未注册时静默）
    public void unregister(int id) {
        if (registered.remove(id) == null) {
            return;
        }
        submitAndWait(() -> invokeInt(UNREGISTER_HOT_KEY, MemorySegment.NULL, id) != 0);
    }

    @Override
    public void close() {
        running = false;
        if (threadId != 0) {
            invokeInt(POST_THREAD_MESSAGE_W, threadId, WM_QUIT, MemorySegment.NULL, MemorySegment.NULL);
        }
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ 消息循环

    private void loop() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment msg = arena.allocate(MSG_SIZE, 8);
            threadId = invokeInt(GET_CURRENT_THREAD_ID);
            ready.countDown();
            while (running) {
                int r = invokeInt(GET_MESSAGE_W, msg, MemorySegment.NULL, 0, 0);
                if (r == 0 || r == -1) {
                    break; // WM_QUIT 或错误
                }
                drainTasks();
                if (msg.get(ValueLayout.JAVA_INT, MSG_MESSAGE_OFFSET) == WM_HOTKEY) {
                    int id = (int) msg.get(ValueLayout.JAVA_LONG, MSG_WPARAM_OFFSET);
                    Platform.runLater(() -> onHotkey.accept(id));
                }
            }
        } finally {
            for (Integer id : registered.keySet()) {
                registered.remove(id);
                invokeInt(UNREGISTER_HOT_KEY, MemorySegment.NULL, id);
            }
            log.info("全局热键线程退出");
        }
    }

    private void drainTasks() {
        Runnable t;
        while ((t = tasks.poll()) != null) {
            t.run();
        }
    }

    private void wake() {
        invokeInt(POST_THREAD_MESSAGE_W, threadId, WM_APP_WAKE, MemorySegment.NULL, MemorySegment.NULL);
    }

    private boolean submitAndWait(Callable<Boolean> task) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        tasks.add(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        wake();
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("热键操作失败", e);
        }
    }

    // ------------------------------------------------------------------ FFM

    private static MethodHandle downcall(String name, FunctionDescriptor fd) {
        MemorySegment sym = USER32.find(name).or(() -> KERNEL32.find(name))
                .orElseThrow(() -> new IllegalStateException("找不到 Win32 符号: " + name));
        return LINKER.downcallHandle(sym, fd);
    }

    private static int invokeInt(MethodHandle mh, Object... args) {
        try {
            return (int) mh.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new IllegalStateException("FFM 调用失败", t);
        }
    }
}
