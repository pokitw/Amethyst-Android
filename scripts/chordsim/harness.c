/*
 * Drive the shipped native key path and ask the one question Minecraft asks.
 *
 * Minecraft decides the profiler pie chart on F3's RELEASE callback:
 *
 *     renderDebugCharts = renderDebug && Screen.hasShiftDown();
 *
 * and Screen.hasShiftDown polls glfwGetKey, whose entire backing store is keyDownBuffer. So the
 * whole bug reduces to one measurable thing: at the instant F3's release callback is dispatched,
 * does a poll of Left Shift still read PRESS? Minecraft's own rule is not executed here, because
 * it is the other program; it is quoted above and reduced to that single assertion.
 */
#include "shim.h"

#define GLFW_RELEASE 0
#define GLFW_PRESS   1
#define KEY_SHIFT_L  340
#define KEY_SHIFT_R  344
#define KEY_F3       292
#define KEY_G        71

static struct pojav_environ_s environ_storage;
struct pojav_environ_s* pojav_environ = &environ_storage;
static jbyte buffer[512];

static int checks = 0, failures = 0;
static void check(int condition, const char* message) {
    checks++;
    if (!condition) { printf("FAIL: %s\n", message); failures++; }
}

void updateWindowSize(void* window) { (void) window; }

/** What glfwGetKey does, and the only thing it does. */
static int index_of(int key) { return key - 31 > 0 ? key - 31 : 0; }
static int poll(int key) { return pojav_environ->keyDownBuffer[index_of(key)]; }

/* What the poll read at each dispatched callback, recorded as the game would have seen it. */
static int seenKey[64], seenAction[64], seenShift[64], seenF3[64], seenCount;

static void recordKey(void* window, int key, int scancode, int action, int mods) {
    (void) window; (void) scancode; (void) mods;
    seenKey[seenCount] = key;
    seenAction[seenCount] = action;
    seenShift[seenCount] = poll(KEY_SHIFT_L);
    seenF3[seenCount] = poll(KEY_F3);
    seenCount++;
}

static void reset(void) {
    memset(buffer, 0, sizeof(buffer));
    pojav_environ->keyDownBuffer = buffer;
    pojav_environ->GLFW_invoke_Key = recordKey;
    pojav_environ->isInputReady = 1;
    pojav_environ->isUseStackQueueCall = 1;   /* every Minecraft 1.13 and later */
    pojav_environ->inEventIndex = 0;
    pojav_environ->outEventIndex = 0;
    pojav_environ->outTargetIndex = 0;
    pojav_environ->shouldUpdateMouse = 0;
    pojav_environ->shouldUpdateMonitorSize = 0;
    seenCount = 0;
}

/** The rewinder hands the pump everything queued so far. */
static void pump(void) {
    pojav_environ->outTargetIndex = pojav_environ->inEventIndex;
    pojavPumpEvents((void*) 0);
    pojav_environ->outEventIndex = pojav_environ->outTargetIndex;
}

/** What the poll read when a given key's given edge was dispatched. */
static int shiftAt(int key, int action) {
    for (int i = 0; i < seenCount; i++) {
        if (seenKey[i] == key && seenAction[i] == action) return seenShift[i];
    }
    return -1;
}

/**
 * One whole tap of a control button, both edges, exactly as ControlButton sends them: every key of
 * an edge goes out synchronously inside one touch event, and the game pumps afterwards.
 */
static void tap(const int* pressOrder, const int* releaseOrder, int count, int pumpBetweenEdges) {
    reset();
    for (int i = 0; i < count; i++) critical_send_key(pressOrder[i], 0, GLFW_PRESS, 0);
    if (pumpBetweenEdges) pump();
    for (int i = 0; i < count; i++) critical_send_key(releaseOrder[i], 0, GLFW_RELEASE, 0);
    pump();
}

int main(void) {
    /*
     * The reported bug, with the fix in place. Shift and F3 on one button, released in the reverse
     * of the press order, both edges inside their own touch event. Minecraft asks about Shift when
     * F3 comes up, so that is what is asserted.
     */
    {
        int down[] = {KEY_SHIFT_L, KEY_F3};
        int up[] = {KEY_F3, KEY_SHIFT_L};
        tap(down, up, 2, 1);
        check(shiftAt(KEY_F3, GLFW_RELEASE) == GLFW_PRESS,
              "Shift did not read as held when F3 was released, so no profiler chart");
        check(poll(KEY_SHIFT_L) == GLFW_RELEASE, "Shift was left held after the tap");
        check(poll(KEY_F3) == GLFW_RELEASE, "F3 was left held after the tap");
    }

    /* And with both edges in one frame, which is what a quick tap on a slow device produces. */
    {
        int down[] = {KEY_SHIFT_L, KEY_F3};
        int up[] = {KEY_F3, KEY_SHIFT_L};
        tap(down, up, 2, 0);
        check(shiftAt(KEY_F3, GLFW_RELEASE) == GLFW_PRESS,
              "a tap inside one frame lost the modifier");
    }

    /* Right Shift is the same key as far as hasShiftDown is concerned. */
    {
        int down[] = {KEY_SHIFT_R, KEY_F3};
        int up[] = {KEY_F3, KEY_SHIFT_R};
        tap(down, up, 2, 1);
        checks++;
        if (pojav_environ->keyDownBuffer[index_of(KEY_SHIFT_R)] != GLFW_RELEASE) {
            printf("FAIL: right shift was left held\n"); failures++;
        }
    }

    /*
     * The press edge too, which is how F3+G is decided. G's press has to see F3 held.
     */
    {
        int down[] = {KEY_F3, KEY_G};
        int up[] = {KEY_G, KEY_F3};
        reset();
        for (int i = 0; i < 2; i++) critical_send_key(down[i], 0, GLFW_PRESS, 0);
        pump();
        int f3AtG = -1;
        for (int i = 0; i < seenCount; i++) {
            if (seenKey[i] == KEY_G && seenAction[i] == GLFW_PRESS) f3AtG = seenF3[i];
        }
        check(f3AtG == GLFW_PRESS, "F3 did not read as held when G was pressed");
        for (int i = 0; i < 2; i++) critical_send_key(up[i], 0, GLFW_RELEASE, 0);
        pump();
    }

    /*
     * The state has to lag the send, not lead it. Sending without pumping must change nothing that
     * a poll can see: that is the whole of the fix, and its one-line revert is the shipped bug.
     */
    {
        reset();
        critical_send_key(KEY_SHIFT_L, 0, GLFW_PRESS, 0);
        check(poll(KEY_SHIFT_L) == GLFW_RELEASE,
              "the key state ran ahead of the callbacks, which is the bug this fixes");
        pump();
        check(poll(KEY_SHIFT_L) == GLFW_PRESS, "the key state never caught up after a pump");
    }

    /* A single key is unchanged, which every player who has never bound a chord depends on. */
    {
        int down[] = {KEY_F3};
        int up[] = {KEY_F3};
        tap(down, up, 1, 1);
        check(seenCount == 2, "a single key produced the wrong number of callbacks");
        check(seenKey[0] == KEY_F3 && seenAction[0] == GLFW_PRESS, "the press was not delivered");
        check(seenKey[1] == KEY_F3 && seenAction[1] == GLFW_RELEASE, "the release was not delivered");
        check(seenF3[0] == GLFW_PRESS, "F3 did not read as held at its own press");
        check(seenF3[1] == GLFW_RELEASE, "F3 still read as held at its own release");
    }

    /* Events are delivered in the order they were sent, and none is dropped or invented. */
    {
        int down[] = {KEY_SHIFT_L, KEY_F3};
        int up[] = {KEY_F3, KEY_SHIFT_L};
        tap(down, up, 2, 0);
        check(seenCount == 4, "the pump lost or invented an event");
        check(seenKey[0] == KEY_SHIFT_L && seenAction[0] == GLFW_PRESS, "order changed at 0");
        check(seenKey[1] == KEY_F3 && seenAction[1] == GLFW_PRESS, "order changed at 1");
        check(seenKey[2] == KEY_F3 && seenAction[2] == GLFW_RELEASE, "order changed at 2");
        check(seenKey[3] == KEY_SHIFT_L && seenAction[3] == GLFW_RELEASE, "order changed at 3");
    }

    /* The clamp: a key below 31 must not write behind the buffer. */
    {
        reset();
        critical_send_key(1, 0, GLFW_PRESS, 0);
        pump();
        check(pojav_environ->keyDownBuffer[0] == GLFW_PRESS, "the low-key clamp moved");
    }

    printf("%d checks\n", checks);
    if (failures) { printf("%d FAILURES\n", failures); return 1; }
    printf("all passed\n");
    return 0;
}
