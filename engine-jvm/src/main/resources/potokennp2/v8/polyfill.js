const __intervalCallbacks = new Map();
let __nextIntervalId = 1;

globalThis.setInterval = function(fn, delay = 0, ...args) {

    const id = __nextIntervalId++;

    __intervalCallbacks.set(id, () => {
        fn(...args);
    });

    __nativeSetInterval(id, delay);

    return id;
};

globalThis.clearInterval = function(id) {
    __intervalCallbacks.delete(id);
    __nativeClearInterval(id);
};

globalThis.__runInterval = function(id) {
    __intervalCallbacks.get(id)?.();
};
