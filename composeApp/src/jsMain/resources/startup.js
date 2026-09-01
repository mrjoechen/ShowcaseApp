// This must run before the application bundle so even entry-point failures are visible.
(function () {
    "use strict";

    const panel = document.getElementById("showcase-startup");
    if (!panel) return;

    const title = document.getElementById("showcase-startup-title");
    const status = document.getElementById("showcase-startup-status");
    const hint = document.getElementById("showcase-startup-hint");
    const details = document.getElementById("showcase-startup-error");
    const retry = document.getElementById("showcase-startup-retry");
    const chinese = /^zh\b/i.test(navigator.language || "");
    const text = chinese ? {
        application: "正在加载应用…",
        engine: "正在启动渲染引擎…",
        font: "正在加载中文字体（约 8 MB）…",
        render: "正在显示界面…",
        hint: "首次访问需要下载字体和渲染引擎，请稍候。",
        failed: "Showcase 启动失败",
        failureHint: "请检查网络后重试。如果仍然失败，请保留下方错误信息。",
        timeout: "启动已超过 120 秒，仍在等待加载。你可以继续等待，或检查网络后重新加载。",
        script: "无法下载应用脚本：",
        unknown: "发生了未知启动错误。",
        retry: "重新加载"
    } : {
        application: "Loading application…",
        engine: "Starting the rendering engine…",
        font: "Loading the Chinese font (about 8 MB)…",
        render: "Drawing the interface…",
        hint: "The first visit may take longer while fonts and the rendering engine download.",
        failed: "Showcase could not start",
        failureHint: "Check your connection and retry. If it still fails, keep the error below for troubleshooting.",
        timeout: "Startup has taken more than 120 seconds. You can keep waiting, or check your connection and reload.",
        script: "Could not download the application script: ",
        unknown: "An unknown startup error occurred.",
        retry: "Reload"
    };
    let completed = false;
    let failed = false;
    let readinessScheduled = false;
    const timeout = setTimeout(function () {
        if (completed || failed) return;
        // A slow connection is not a failed boot: a later rendered frame can still finish.
        status.textContent = text.timeout;
        retry.hidden = false;
    }, 120000);

    status.textContent = text.application;
    hint.textContent = text.hint;
    retry.textContent = text.retry;
    retry.addEventListener("click", function () { window.location.reload(); });

    function fail(reason) {
        if (completed || failed) return;
        failed = true;
        clearTimeout(timeout);
        title.textContent = text.failed;
        status.textContent = text.failureHint;
        hint.hidden = true;
        // Error messages may contain server-controlled content. Never interpret it as HTML.
        details.textContent = String(reason && reason.message ? reason.message : reason || text.unknown).slice(0, 2000);
        details.hidden = false;
        retry.hidden = false;
        panel.setAttribute("role", "alert");
    }

    function onError(event) {
        if (event.target && event.target.tagName === "SCRIPT") {
            // Hosting providers may inject optional analytics scripts. Their blocked
            // downloads must not hide an otherwise working application.
            if (event.target.id === "showcase-app-script") {
                fail(text.script + (event.target.getAttribute("src") || "ShowcaseApp.js"));
            }
        } else if (event.message || event.error) {
            fail(event.error || event.message);
        }
    }

    function onRejection(event) {
        fail(event.reason);
    }

    window.addEventListener("error", onError, true);
    window.addEventListener("unhandledrejection", onRejection);
    window.ShowcaseStartup = Object.freeze({
        setStage: function (stage) {
            if (!completed && !failed && text[stage]) status.textContent = text[stage];
        },
        fail: fail,
        ready: function () {
            if (completed || failed || readinessScheduled) return;
            readinessScheduled = true;
            // Called after Compose draws its content. Let that frame reach the screen
            // before removing the HTML overlay; failures during that frame remain visible.
            requestAnimationFrame(function () {
                requestAnimationFrame(function () {
                    if (completed || failed) return;
                    completed = true;
                    clearTimeout(timeout);
                    window.removeEventListener("error", onError, true);
                    window.removeEventListener("unhandledrejection", onRejection);
                    panel.remove();
                });
            });
        }
    });
})();
